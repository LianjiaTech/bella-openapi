# ApiKey Manager 委托管理设计文档

## 1. 概述

ApiKey Manager 体系允许 AK 所有者将某个 AK 的日常管理权限委托给指定用户（manager），使其无需拥有该 AK 的所有权，即可对 AK 及其子 AK 执行重置、创建子AK、修改管理人等操作。

### 目标
- 支持跨所有者的 AK 管理委托，覆盖个人 AK 和组织 AK 两种场景
- manager 拥有近似 owner 的操作权限，但不可执行 TRANSFER（转移所有权）
- 父 AK 的管理权限自动传递给所有子 AK，子 AK 的管理人变更不影响 manager 对子 AK 的控制权

## 2. 设计

### 2.1 数据模型

Manager 关系存储在 `apikey` 表的两个字段中，每个 AK 只有一个 manager：

| 字段 | 类型 | 说明 |
|------|------|------|
| `manager_code` | VARCHAR | 管理人的 userCode |
| `manager_name` | VARCHAR | 管理人的显示名称 |

> `manager_code` 存储规则与 `owner_code` 相同，存在双轨制：CAS 场景（`sourceId == userId`）存 sourceId，OAuth 场景（GitHub/Google）存 userId。后续统一规范为只存 sourceId 后做迁移。

### 2.2 权限边界

| 操作 | owner | manager | 说明 |
|------|:-----:|:-------:|------|
| QUERY | ✓ | ✓ | 查询 AK 信息 |
| RESET | ✓ | ✓ | 重置密钥 |
| RENAME | ✓ | ✓ | 重命名 |
| UPDATE_MANAGER | ✓ | ✓ | 修改管理人（可下授他人） |
| CERTIFY | ✓ | ✓ | 安全认证 |
| UPDATE_QPS | ✓ | ✓ | 修改QPS限制 |
| CHANGE_STATUS | ✓ | ✓ | 启用/停用 |
| CREATE_CHILD | ✓ | ✓ | 创建子AK |
| VIEW_TRANSFER_HISTORY | ✓ | ✓ | 查询转移历史 |
| TRANSFER | ✓ | - | **owner 专属**，manager 不可转移他人的 AK |

### 2.3 子 AK 继承规则

```
创建子 AK（createByParentCode）
  └─ 子 AK.managerCode = 父 AK.managerCode
  └─ 子 AK.managerName = 父 AK.managerName

更新父 AK 的管理人（updateManager）
  ├─ UPDATE apikey SET manager_code=?, manager_name=? WHERE code = parentCode
  ├─ UPDATE apikey SET manager_code=?, manager_name=? WHERE parent_code = parentCode  (syncManagerToChildren)
  └─ 清除父 AK 及所有子 AK 的缓存
```

### 2.4 权限追溯规则

对子 AK 进行操作时，权限校验向上追溯父 AK：

```
场景：manager 将子 AK 的管理人改为他人
  → 子 AK.managerCode 不再是 manager 自己
  → 直接校验子 AK → UNRELATED → 若在此拒绝则权限丢失（错误）

正确做法：
  → 直接校验子 AK → UNRELATED
  → 子 AK.parentCode 非空 → 查父 AK
  → manager 是父 AK 的 MANAGER → 继承权限 → 放行
```

**结论**：拥有父 AK 的 OWNER/MANAGER 关系，即对其所有子 AK 拥有对等权限，不受子 AK 的 `manager_code` 变更影响。

## 3. 核心流程

### 3.1 设置管理人

```
┌─────────────────┐
│  POST           │
│ /console/apikey │
│  /manager/update│
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────┐
│  ApikeyConsoleController        │
│  validateParams:                │
│   - code 非空                   │
│   - managerUserId 非空          │
│     OR (managerCode+managerName │
│          均非空)                │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│  ApikeyService.updateManager()  │
│                                 │
│  1. queryByUniqueKey(code)      │
│  2. check(existing,             │
│       UPDATE_MANAGER)           │
│  3. resolveManagerCode()        │
│     - 传了 managerUserId →      │
│       查 user 表，按双轨制      │
│       计算 managerCode          │
│     - 未传 → 直接用入参         │
│  4. update(db, code)            │
│  5. syncManagerToChildren()     │
│  6. clearApikeyCache(主+子)     │
└─────────────────────────────────┘
```

**managerCode 计算规则（双轨制）**：

```
if (operator.sourceId == operator.userId.toString())
  → CAS 场景 → managerCode = targetUser.sourceId
else
  → OAuth 场景 → managerCode = targetUser.id.toString()
```

### 3.2 查询管理的 AK

```
GET /console/apikey/page
  ?managerCode={userId}
  &status=active
  &page=n
  [&searchParam=...]
  [&onlyChild=true]
```

```
pageApikey(condition)
  │
  ├─ fillPermissionCode()
  │    └─ 普通用户 + 传了 managerCode
  │         → 不叠加 personalCode（否则无法查到他人/组织的AK）
  │
  └─ fillManagerCode()
       ├─ SYSTEM 类型 AK → 不受限
       ├─ 管理员（console/all）→ 不受限，支持 managerSearch 模糊查询
       └─ 普通用户
            ├─ managerCode 必须等于自己的 userId
            └─ 禁止使用 managerSearch（防止遍历他人管理关系）
```

**`onlyChild` 参数**：`true` 时后端过滤 `parent_code != ''`，直接返回子 AK，分页计数准确，避免前端再次过滤带来的计数偏差。

## 4. 组件交互

```
┌─────────────────────────────────────────────────────┐
│                    前端入口                          │
│                                                     │
│  ManagerPage（/manager）                            │
│  └─ ManagedKeysTable                               │
│       ├─ DelegatedSection（委托管理-顶层AK）         │
│       │    → getManagerApiKeys(page, userId)        │
│       │    → 操作：进入子AK管理页                   │
│       └─ AssignedSection（分配给我-子AK）            │
│            → getManagerApiKeys(page, userId,        │
│                search, onlyChild=true)              │
│            → 操作：重置密钥                         │
│                                                     │
│  ApiKeyAdminPage（/apikey-admin）                    │
│  └─ AdminKeysTable                                  │
│       └─ 设置管理人 → ManagerDialog                 │
│                                                     │
│  SubAkPage（/apikey/sub-ak/[code]?viewer=manager）  │
│  └─ SubAkTable（fetchMode=admin）                   │
│       └─ 设置管理人 → ManagerDialog                 │
└─────────────────────────────────────────────────────┘
                          │
                          │  HTTP
                          ▼
┌─────────────────────────────────────────────────────┐
│              ApikeyConsoleController                 │
│  POST /console/apikey/manager/update                │
│  GET  /console/apikey/page?managerCode=...          │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                 ApikeyService                        │
│  updateManager()    fillManagerCode()               │
│  fillPermissionCode()                               │
└──────────────────────┬──────────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
┌──────────────┐ ┌──────────┐ ┌──────────────────────┐
│ ApikeyRepo   │ │UserRepo  │ │ AkPermissionChecker  │
│ update()     │ │queryById │ │ check(existing,      │
│ syncManager  │ │          │ │  UPDATE_MANAGER)     │
│ ToChildren() │ │          │ └──────────────────────┘
└──────────────┘ └──────────┘
```

### ManagerDialog 交互流程

```
打开 ManagerDialog（akCode, akDisplay）
  │
  ├─ 用户输入搜索词（useDeferredValue + 500ms 防抖）
  │    └─ searchUserInfo(keyword) → 展示用户列表
  │
  └─ 点击某用户的"设置"按钮
       ├─ setSubmittingId(user.id)（单行 loading，不影响其他行）
       ├─ updateManager({ code: akCode, managerUserId: user.id })
       ├─ 成功 → toast.success → onSuccess() → onClose()
       └─ 失败 → toast.error（保持弹窗打开）
```

## 5. 前端页面说明

### 5.1 我管理的AK（/manager）

| 区块 | 数据来源 | 可执行操作 |
|------|----------|-----------|
| 委托管理（顶层AK） | `managerCode=userId`（不含子AK） | 进入子AK管理页 |
| 分配给我（子AK） | `managerCode=userId&onlyChild=true` | 重置密钥 |

两个区块始终挂载（CSS `hidden` 控制显隐），切换 Tab 不触发重新请求。Tab badge 实时显示各区块数量。

### 5.2 子AK管理页（viewer=manager）

通过 `useSubAkCapability(viewer='manager')` 计算操作能力：

| 能力 | 值 | 说明 |
|------|----|------|
| `fetchMode` | `admin` | 不绑定 ownerCode，由后端 manager 权限过滤 |
| `canCreate` | ✓ | 可创建子AK |
| `canReset` | ✓ | 可重置子AK |
| `canSetManager` | ✓ | 可为子AK指定管理者 |
| `canDelete` | - | 不可删除 |
| `backHref` | `/manager` | 返回"我管理的AK"页面 |


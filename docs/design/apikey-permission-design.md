# ApiKey 权限体系设计文档

## 1. 概述

ApiKey 权限体系基于 `(调用者身份, 调用者与目标AK的关系, 操作类型)` 三元组进行权限判断，将原先散落在各业务方法中的权限逻辑统一收敛到权限矩阵和单一检查入口，支持按操作粒度精细控制、Manager 委托管理、子AK权限追溯等能力。

### 目标
- 权限规则集中在一张矩阵中，不散落在各方法内
- 单一检查入口 `checkPermission(targetCode, operation)`，各业务方法只需传入操作类型
- 新增操作类型或调整权限规则只改矩阵，不改业务逻辑
- 支持 Manager 委托管理场景：某人被指定为某个 AK 的负责人，对该 AK 及其子 AK 拥有近似 owner 的操作权限

## 2. 设计

### 2.1 基础体系

**rolecode**

| rolecode | 含义 | 路径权限 |
|----------|------|---------|
| `all` | 超级管理员 | `/**`（全部路径） |
| `console` | 控制台管理员 | `/v*/**`、`/console/**` |
| `high` | 高级个人AK | `/v*/**`、`/console/apikey/**`（排除quota/role更新） |
| `low` | 基础个人AK | `/v*/**`、`/console/apikey/**`（排除create/quota/role） |

**ownerType**

| ownerType | 含义 |
|-----------|------|
| `system` | 系统级AK，由运营直接创建 |
| `org` | 组织级AK |
| `person` | 个人AK |
| `console` | 用户登录时自动生成的控制台AK |

### 2.2 核心枚举

#### 操作类型（AkOperation）

```java
public enum AkOperation {
    QUERY,                 // 查询AK信息（含分页列表）
    RESET,                 // 重置密钥
    RENAME,                // 重命名
    UPDATE_ROLE,           // 修改角色/路径权限（仅管理员可用）
    UPDATE_MANAGER,        // 修改管理人（owner/manager 均可操作）
    CERTIFY,               // 安全认证
    UPDATE_QUOTA,          // 修改配额
    UPDATE_QPS,            // 修改QPS限制
    CHANGE_STATUS,         // 启用/停用
    CREATE_CHILD,          // 创建子AK
    TRANSFER,              // 转移所有者（owner 专属）
    VIEW_TRANSFER_HISTORY, // 查询转移历史
    BIND_SERVICE           // 绑定服务
}
```

> `UPDATE_ROLE` 与 `UPDATE_MANAGER` 刻意分离：前者修改 roleCode/路径权限，仅管理员级别可操作；后者修改管理人，owner 和 manager 均可操作。

#### 关系类型（AkRelation）

```java
public enum AkRelation {
    OWNER,      // 调用者是目标AK的所有者（ownerCode 匹配）
    MANAGER,    // 调用者是目标AK的管理者（manager_code 匹配）
    SAME_ORG,   // 调用者与目标AK同属一个 org（当前预留）
    UNRELATED,  // 无直接关系
}
```

### 2.3 权限判断流程

```
┌─────────────────────────────────────────┐
│         checkPermission(code, op)        │
└────────────────────┬────────────────────┘
                     │
                     ▼
        ┌────────────────────────┐
        │  caller == null?        │  (Console 登录用户，无AK)
        └──────────┬─────────────┘
                   │
        ┌──────────┴──────────┐
        │ 是                  │ 否
        ▼                     ▼
┌──────────────────┐  ┌──────────────────────┐
│checkOperator     │  │ caller.ownerType      │
│Permission()      │  │   == system?          │
└──────────────────┘  └──────────┬───────────┘
                                 │
                      ┌──────────┴──────────┐
                      │ 是                  │ 否
                      ▼                     ▼
             ┌─────────────────┐  ┌──────────────────────┐
             │目标非system →   │  │  resolveRelation()    │
             │全放行           │  │  →AkPermissionMatrix  │
             │目标为system →   │  │   .isAllowed()        │
             │拒绝             │  └──────────────────────┘
             └─────────────────┘
```

#### Operator 路径（Console 登录用户）

```
checkOperatorPermission(targetDb, operation)
  │
  ├─ isOwner: ownerType ∈ {person,console} && ownerCode == userId
  │    └─ → 允许 OPERATOR_OWNER_OPS
  │
  ├─ isManager: manager_code == userId
  │    └─ → 允许 OPERATOR_MANAGER_OPS
  │
  └─ parentCode 非空（目标为子AK）
       └─ 查父AK，重复 isOwner / isManager 判断
            ├─ 父AK isOwner → 允许 OPERATOR_OWNER_OPS
            └─ 父AK isManager → 允许 OPERATOR_MANAGER_OPS
```

#### AK 调用路径

```
resolveRelation(caller, targetDb)
  │
  ├─ isOwner(caller, targetDb)   → OWNER
  ├─ isManager(caller, targetDb) → MANAGER   (仅 person/console 类型AK参与)
  ├─ isSameOrg(caller, targetDb) → SAME_ORG  (当前预留，orgCodes 为空集)
  ├─ parentCode 非空（目标为子AK）
  │    └─ 查父AK，重复 OWNER / MANAGER 判断
  └─ → UNRELATED
```

### 2.4 权限矩阵

#### system ownerType（不经过矩阵）

| 目标 ownerType | 结果 |
|----------------|------|
| 非 `system` | ✓ 全部操作放行 |
| `system` | - 拒绝 |

#### all roleCode

| 关系 | QUERY | RESET | RENAME | UPDATE_ROLE | UPDATE_MANAGER | CERTIFY | UPDATE_QUOTA | UPDATE_QPS | CHANGE_STATUS | CREATE_CHILD | TRANSFER | VIEW_TRANSFER_HISTORY | BIND_SERVICE |
|------|:-----:|:-----:|:------:|:-----------:|:--------------:|:-------:|:------------:|:----------:|:-------------:|:------------:|:--------:|:--------------------:|:------------:|
| ANY | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

#### console roleCode

| 关系 | QUERY | RESET | RENAME | UPDATE_ROLE | UPDATE_MANAGER | CERTIFY | UPDATE_QUOTA | UPDATE_QPS | CHANGE_STATUS | CREATE_CHILD | TRANSFER | VIEW_TRANSFER_HISTORY | BIND_SERVICE |
|------|:-----:|:-----:|:------:|:-----------:|:--------------:|:-------:|:------------:|:----------:|:-------------:|:------------:|:--------:|:--------------------:|:------------:|
| OWNER | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| MANAGER | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| SAME_ORG | ✓ | ✓ | ✓ | - | - | - | - | - | ✓ | - | - | - | - |
| UNRELATED | ✓ | - | - | - | - | - | - | - | - | - | - | - | - |

#### high / low roleCode

| 关系 | QUERY | RESET | RENAME | UPDATE_ROLE | UPDATE_MANAGER | CERTIFY | UPDATE_QUOTA | UPDATE_QPS | CHANGE_STATUS | CREATE_CHILD | TRANSFER | VIEW_TRANSFER_HISTORY | BIND_SERVICE |
|------|:-----:|:-----:|:------:|:-----------:|:--------------:|:-------:|:------------:|:----------:|:-------------:|:------------:|:--------:|:--------------------:|:------------:|
| OWNER | ✓ | ✓ | ✓ | - | ✓ | - | - | - | ✓ | ✓ | ✓ | ✓ | - |
| MANAGER | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| SAME_ORG | - | - | - | - | - | - | - | - | - | - | - | - | - |
| UNRELATED | - | - | - | - | - | - | - | - | - | - | - | - | - |

#### Operator（Console 登录用户）操作集

| 身份 | QUERY | RESET | RENAME | UPDATE_MANAGER | CERTIFY | UPDATE_QPS | CHANGE_STATUS | CREATE_CHILD | TRANSFER | VIEW_TRANSFER_HISTORY |
|------|:-----:|:-----:|:------:|:--------------:|:-------:|:----------:|:-------------:|:------------:|:--------:|:--------------------:|
| OWNER | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| MANAGER | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | - | ✓ |
| 其他 | - | - | - | - | - | - | - | - | - | - |

> TRANSFER 是 owner 专属操作，manager 不可转移他人的 AK。

## 3. Manager 委托管理

### 3.1 数据模型

Manager 关系存储在 `apikey` 表的 `manager_code` / `manager_name` 字段中，每个 AK 只有一个 manager：

| 字段 | 说明 |
|------|------|
| `manager_code` | 管理人的 userCode |
| `manager_name` | 管理人的显示名称 |

> `manager_code` 与 `owner_code` 存在双轨制：CAS 场景存 sourceId，OAuth 场景存 userId。后续统一规范为只存 sourceId 后做迁移。

### 3.2 子AK继承规则

```
创建子AK（createByParentCode）
  └─ db.setManagerCode(parent.getManagerCode())
  └─ db.setManagerName(parent.getManagerName())

更新父AK的管理人（updateManager）
  └─ apikeyRepo.update(db, parentCode)
  └─ apikeyRepo.syncManagerToChildren(parentCode, db)   // 同步到所有子AK
  └─ clearApikeyCache(parent + all children)
```

### 3.3 子AK权限追溯

当对子AK进行权限校验时，若直接关系（OWNER/MANAGER）不满足，向上追溯父AK：

```
场景：manager 将子AK的管理人改为他人
  → 子AK.managerCode 不再是 manager 自己
  → 直接校验子AK → UNRELATED → 拒绝（错误）

正确做法：
  → 直接校验子AK → UNRELATED
  → 查父AK，manager 是父AK的 MANAGER
  → 继承父AK的 MANAGER 权限 → 放行（正确）
```

**结论**：拥有父AK的 OWNER/MANAGER 关系，即对其所有子AK拥有对等权限，不受子AK的 `manager_code` 变更影响。

## 4. 查询权限过滤

分页查询接口（`/console/apikey/page`）在进入 SQL 之前，由 `fillPermissionCode` + `fillManagerCode` 联合控制数据范围：

```
┌─────────────────────────────────────────────┐
│           pageApikey(condition)              │
└──────────────────┬──────────────────────────┘
                   │
          ┌────────▼────────┐
          │fillPermissionCode│
          └────────┬────────┘
                   │
     ┌─────────────┼─────────────┐
     │             │             │
     ▼             ▼             ▼
 管理员         传了parentCode  传了managerCode
 (console/all) │               │
 不叠加过滤     checkPermission  由fillManagerCode
               (parentCode,    校验managerCode==userId
                QUERY)         不叠加personalCode
               不叠加personalCode
                               │
                               ▼
                    其他：叠加personalCode=userId
                    只查自己的AK
```

| 调用方 | 条件 | 行为 |
|--------|------|------|
| 管理员（roleCode ∈ `console`/`all`） | 任意 | 不叠加所有者过滤，查全量 |
| 普通用户 | 传了 `parentCode` | 校验对父AK有 QUERY 权限后放行，不叠加 `personalCode` |
| 普通用户 | 传了 `managerCode` | 校验 `managerCode == userId`，不叠加 `personalCode`（否则无法查到他人/组织的AK） |
| 普通用户 | 其他 | 叠加 `personalCode = userId`，只查自己的AK |

## 5. 组件交互

```
┌─────────────────────────────────────────────┐
│              ApikeyService                   │
│  checkPermission(code, AkOperation.RESET)    │
│  checkPermission(code, AkOperation.TRANSFER) │
│  ...（各方法只传操作类型，不含权限逻辑）      │
└─────────────────────┬───────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────┐
│           AkPermissionChecker               │
│  check(ApikeyDB, AkOperation)               │
│  - checkOperatorPermission()  (无AK分支)    │
│  - resolveRelation()          (有AK分支)    │
│  - parentCode 追溯            (子AK场景)    │
└──────────┬──────────────────────────────────┘
           │                        │
           ▼                        ▼
┌──────────────────┐    ┌──────────────────────┐
│ AkPermissionMatrix│   │     ApikeyRepo        │
│ isAllowed(        │   │ queryByUniqueKey()    │
│  roleCode,        │   │ (父AK追溯时查询)      │
│  relation,        │   └──────────────────────┘
│  operation)       │
└──────────────────┘
```

### 各业务方法操作类型映射

| 方法 | AkOperation |
|------|-------------|
| `reset` | `RESET` |
| `updateRole` | `UPDATE_ROLE` |
| `updateManager` | `UPDATE_MANAGER` |
| `certify` | `CERTIFY` |
| `updateQuota` | `UPDATE_QUOTA` |
| `updateQpsLimit` | `UPDATE_QPS` |
| `changeStatus` | `CHANGE_STATUS` |
| `createByParentCode` | `CREATE_CHILD`（校验父AK） |
| `updateSubApikey` | `CREATE_CHILD`（校验父AK） |
| `transferApikeyOwner` | `TRANSFER` |
| `getTransferHistory` | `VIEW_TRANSFER_HISTORY` |

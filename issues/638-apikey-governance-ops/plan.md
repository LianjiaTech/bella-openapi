# Plan: #638 提供 API Key owner 与 parent 治理接口

## 目标
补齐 API Key 的平台级治理能力，支持：
- 将 AK 的 owner 从个人调整为组织或项目
- 将 AK 迁移到指定父 AK 名下
- 在治理过程中同步维护 manager、parent_code、变更历史与缓存一致性

最终交付统一的后端能力，包括：
- `POST /console/apikey/owner/change`
- `POST /console/apikey/parent/change`
- `GET /console/apikey/change/history`

并保证这三类能力在权限、事务、审计、缓存行为上与现有 API Key 体系兼容。

## 非目标
- 不扩展现有 `/console/apikey/owner/transfer` 为多用途接口
- 不支持将 AK 挂到子 AK 名下
- 不支持多层 AK 树结构，仍维持两层关系
- 不向普通 owner、manager 开放治理接口
- 不在本期统一 `owner_code` / `manager_code` 的双轨编码规则
- 不在本期处理前端页面、弹窗或联调交互实现

## 验收标准
- 新增 `/console/apikey/owner/change`，支持修改 AK 的 owner 信息
- 新增 `/console/apikey/parent/change`，支持迁移 AK 到目标父 AK 名下
- 若 `owner/change` 作用于父 AK，则会同步更新直属子 AK 的 owner 与 manager
- 若 `parent/change` 作用于父 AK，则会按扁平化规则将该父 AK 及其直属子 AK 一并迁移到目标父 AK 下
- 迁移后 manager 必须基于治理前源 AK 的 `owner_code` / `owner_name` 回写，不能重新推导
- 新增 `/console/apikey/change/history`，可返回 `owner_transfer` / `owner_change` / `parent_change`
- 任一治理动作中，主数据更新与历史日志写入必须处于同一事务内，任一步失败整体回滚
- 事务提交后会清理所有受影响 AK 的缓存
- 权限上仅 `console` / `all` 可执行治理动作和查看统一变更历史

## 约束
- 仅允许对 `active` 状态 AK 进行治理
- `owner/change` 的 `targetOwnerType` 仅允许 `org` 或 `project`
- `targetOwnerCode` / `targetOwnerName` 允许为空；为空时沿用原值
- 合成后的目标 owner 若与当前完全一致，必须拒绝执行
- `parent/change` 的目标 AK 必须存在、为父 AK、状态为 `active`
- 源 AK 与目标父 AK 不可相同
- 若源 AK 已经挂在目标父 AK 下，必须拒绝重复迁移
- 治理时不新增 `apikey` 主表字段，继续沿用 `owner_type`、`owner_code`、`owner_name`、`manager_code`、`manager_name`、`parent_code`
- 缓存清理必须依据事务前收集的受影响 AK 集合执行，不能依赖事务后通过当前 `parent_code` 关系反查

## 变更范围
- `api/sdk/src/main/java/com/ke/bella/openapi/apikey/ApikeyOps.java`
- `api/sdk/src/main/java/com/ke/bella/openapi/apikey/AkOperation.java`
- `api/sdk/src/main/java/com/ke/bella/openapi/apikey/AkPermissionMatrix.java`
- `api/sdk/src/main/java/com/ke/bella/openapi/apikey/ApikeyChangeLog.java`
- `api/server/src/main/java/com/ke/bella/openapi/console/ApikeyConsoleController.java`
- `api/server/src/main/java/com/ke/bella/openapi/service/ApikeyService.java`
- `api/server/src/main/java/com/ke/bella/openapi/service/AkPermissionChecker.java`
- `api/server/src/main/java/com/ke/bella/openapi/db/repo/ApikeyRepo.java`
- `api/server/src/main/java/com/ke/bella/openapi/db/repo/ApikeyChangeLogRepo.java`
- `api/server/src/main/java/com/ke/bella/openapi/listener/ChangeApikeyListener.java`
- `api/server/src/main/java/com/ke/bella/openapi/listener/TransferApikeyListener.java`
- `api/server/src/main/java/com/ke/bella/openapi/event/` 下与变更事件相关的链路
- 数据库脚本目录中与 `apikey_change_log` 相关的 DDL（如仓库中尚未提交）

## 实现思路

### 1. 补齐治理请求模型与返回模型
- **目标**：为治理接口定义统一 DTO，避免 controller 层散参
- **涉及文件**：
  - `api/sdk/src/main/java/com/ke/bella/openapi/apikey/ApikeyOps.java`
- **具体改动**：
  - 定义 `ChangeOwnerOp`
    - `code`
    - `targetOwnerType`
    - `targetOwnerCode`
    - `targetOwnerName`
    - `reason`
  - 定义 `ChangeParentOp`
    - `code`
    - `targetParentCode`
    - `reason`
  - 定义 `ChangeResult`
    - `code`
    - `action`
    - `affectedCount`

### 2. 明确治理类操作的权限入口
- **目标**：让治理能力进入统一权限检查链路，并严格限制为平台管理员能力
- **涉及文件**：
  - `api/sdk/src/main/java/com/ke/bella/openapi/apikey/AkOperation.java`
  - `api/sdk/src/main/java/com/ke/bella/openapi/apikey/AkPermissionMatrix.java`
  - `api/server/src/main/java/com/ke/bella/openapi/service/AkPermissionChecker.java`
- **具体改动**：
  - 在 `AkOperation` 中补齐：
    - `CHANGE_OWNER`
    - `CHANGE_PARENT`
    - `VIEW_CHANGE_HISTORY`
  - 在 `AkPermissionMatrix` 中：
    - 对 `all` 继续全放行
    - 对 `console` 在 `UNRELATED` 关系下开放：
      - `CHANGE_OWNER`
      - `CHANGE_PARENT`
      - `VIEW_CHANGE_HISTORY`
    - 对 `high` / `low` 的 owner、manager 不开放上述治理权限
  - 在 `AkPermissionChecker` 中保持管理员角色通过 `roleCode=console/all` 进入治理权限校验，不向普通 owner / manager 扩散

### 3. 在 repo 层补齐 owner / parent 批量更新能力
- **目标**：支持 owner 治理和 parent 迁移的原子更新
- **涉及文件**：
  - `api/server/src/main/java/com/ke/bella/openapi/db/repo/ApikeyRepo.java`
- **具体改动**：
  - 增加 `batchUpdateOwnerAndManagerByParentCode(ApikeyDB updateDB, String parentCode)`
    - 批量修改直属子 AK 的 owner 与 manager
  - 增加 `updateParentAndManagerByCode(...)`
    - 迁移单个 AK 到目标父 AK，并回写 manager
  - 增加 `batchUpdateParentAndManagerByParentCode(...)`
    - 父 AK 扁平化迁移时，批量迁移直属子 AK
  - 所有更新统一写入 `muid` / `mu_name`

### 4. 实现 owner/change 事务
- **目标**：按设计完成 owner 治理主流程
- **涉及文件**：
  - `api/server/src/main/java/com/ke/bella/openapi/service/ApikeyService.java`
- **具体改动**：
  - 查询源 AK，校验存在、状态为 `active`、具备 `CHANGE_OWNER` 权限
  - 计算目标 owner：
    - `owner_type = targetOwnerType`
    - `owner_code = targetOwnerCode` 非空时覆盖，否则沿用原值
    - `owner_name = targetOwnerName` 非空时覆盖，否则沿用原值
  - 若目标 owner 与当前完全一致，拒绝执行
  - 基于源 AK 变更前快照回写 manager：
    - `manager_code = source.ownerCode`
    - `manager_name = source.ownerName`
  - 更新源 AK
  - 若源 AK 为父 AK，则批量更新直属子 AK 的：
    - `owner_type`
    - `owner_code`
    - `owner_name`
    - `manager_code`
    - `manager_name`
  - 记录所有受影响 AK code，用于日志与缓存
  - 写入一条 `owner_change` 类型的统一变更日志
  - 事务提交后发布 `ApiKeyChangeEvent`
  - 返回 `ChangeResult(action=owner_change, affectedCount=...)`

#### 4.1 子 AK owner 治理
- **目标**：保证子 AK 场景只影响自身
- **涉及文件**：
  - `api/server/src/main/java/com/ke/bella/openapi/service/ApikeyService.java`
- **具体改动**：
  - 当源 AK 存在 `parent_code` 时，仅更新该子 AK 本身
  - 不影响同父其他子 AK
  - 不影响父 AK

### 5. 实现 parent/change 事务
- **目标**：按设计完成 AK 迁移与父 AK 扁平化迁移
- **涉及文件**：
  - `api/server/src/main/java/com/ke/bella/openapi/service/ApikeyService.java`
- **具体改动**：
  - 查询源 AK 与目标父 AK
  - 校验：
    - 源 AK 存在且为 `active`
    - 目标 AK 存在且为 `active`
    - 目标 AK 必须是父 AK（`parent_code=''`）
    - 源 AK 与目标 AK 不相同
    - 源 AK 当前未挂在该目标父 AK 下
  - 校验权限：
    - 对源 AK 具备 `CHANGE_PARENT`
    - 对目标父 AK 具备 `CREATE_CHILD`
  - manager 回写必须使用源 AK 治理前 owner 信息：
    - `manager_code = source.ownerCode`
    - `manager_name = source.ownerName`

#### 5.1 源 AK 为子 AK
- **目标**：完成单条迁移
- **涉及文件**：
  - `api/server/src/main/java/com/ke/bella/openapi/service/ApikeyService.java`
  - `api/server/src/main/java/com/ke/bella/openapi/db/repo/ApikeyRepo.java`
- **具体改动**：
  - 仅更新源 AK：
    - `parent_code = targetParentCode`
    - `manager_code = source.ownerCode`
    - `manager_name = source.ownerName`
  - 不修改 owner
  - 不影响其他 AK
  - 写入 `parent_change` 日志
  - 发布包含源 AK 的缓存清理事件

#### 5.2 源 AK 为父 AK（扁平化迁移）
- **目标**：保持两层结构，不形成多层树
- **涉及文件**：
  - `api/server/src/main/java/com/ke/bella/openapi/service/ApikeyService.java`
  - `api/server/src/main/java/com/ke/bella/openapi/db/repo/ApikeyRepo.java`
- **具体改动**：
  - 查询源父 AK 的直属子 AK 列表
  - 更新源父 AK：
    - `parent_code = targetParentCode`
    - `manager_code = source.ownerCode`
    - `manager_name = source.ownerName`
  - 批量更新原直属子 AK：
    - `parent_code = targetParentCode`
    - `manager_code = source.ownerCode`
    - `manager_name = source.ownerName`
  - 不修改 owner
  - 受影响集合包含：
    - 源父 AK
    - 所有原直属子 AK
  - 写入一条 `parent_change` 日志
  - 发布统一缓存清理事件

### 6. 建立统一变更历史能力
- **目标**：将治理动作沉淀到 `apikey_change_log`
- **涉及文件**：
  - `api/sdk/src/main/java/com/ke/bella/openapi/apikey/ApikeyChangeLog.java`
  - `api/server/src/main/java/com/ke/bella/openapi/db/repo/ApikeyChangeLogRepo.java`
  - `api/server/src/main/java/com/ke/bella/openapi/service/ApikeyService.java`
  - `api/server/src/main/java/com/ke/bella/openapi/console/ApikeyConsoleController.java`
- **具体改动**：
  - 定义 `ApikeyChangeLog` DTO 字段，覆盖：
    - `action_type`
    - `ak_code`
    - `affected_codes`
    - `from_*`
    - `to_*`
    - `reason`
    - `operator_uid`
    - `operator_name`
    - `status`
    - `ctime/mtime`
  - 在 `ApikeyChangeLogRepo` 中补齐插入与查询
  - 在 service 中新增 `getChangeHistory`
  - 在 controller 中暴露 `/console/apikey/change/history`

### 7. 兼容旧 owner/transfer 到统一 change log
- **目标**：不破坏既有转移接口，同时让统一历史具备完整上下文
- **涉及文件**：
  - `api/server/src/main/java/com/ke/bella/openapi/service/ApikeyService.java`
  - `api/server/src/main/java/com/ke/bella/openapi/db/repo/ApikeyTransferLogRepo.java`
  - `api/server/src/main/java/com/ke/bella/openapi/db/repo/ApikeyChangeLogRepo.java`
- **具体改动**：
  - 保留 `/console/apikey/owner/transfer` 原语义与 `transfer/history`
  - 在 `transferApikeyOwner(...)` 成功时，同时补写 `action_type=owner_transfer` 的 `apikey_change_log`
  - 让 `/change/history` 可以覆盖 `owner_transfer` / `owner_change` / `parent_change`
  - 保持 `transfer/history` 仅返回旧 owner transfer 视图

### 8. 统一变更事件与缓存清理
- **目标**：确保治理后 whoami / 权限结果一致
- **涉及文件**：
  - `api/server/src/main/java/com/ke/bella/openapi/listener/ChangeApikeyListener.java`
  - `api/server/src/main/java/com/ke/bella/openapi/listener/TransferApikeyListener.java`
  - `api/server/src/main/java/com/ke/bella/openapi/service/ApikeyService.java`
- **具体改动**：
  - 治理动作统一发布 `ApiKeyChangeEvent`
  - 事件中传递完整受影响 `akCode` 列表
  - `ChangeApikeyListener` 在 `AFTER_COMMIT` 异步清理缓存
  - 保持 `TransferApikeyListener` 兼容旧 transfer 行为
  - 明确不通过“事务后按当前 parent_code 反查”决定清理对象，避免扁平化迁移后漏清理

### 9. 补齐 controller 暴露与参数校验
- **目标**：提供清晰稳定的 console API 入口
- **涉及文件**：
  - `api/server/src/main/java/com/ke/bella/openapi/console/ApikeyConsoleController.java`
- **具体改动**：
  - 新增：
    - `POST /console/apikey/owner/change`
    - `POST /console/apikey/parent/change`
    - `GET /console/apikey/change/history`
  - controller 层只做基础参数校验：
    - `code`
    - `targetOwnerType`
    - `targetParentCode`
    - `akCode`
  - 业务校验下沉到 service

### 10. 补齐数据库与自测验证
- **目标**：保证新日志表和关键链路具备落地条件
- **涉及文件**：
  - 数据库脚本目录中与 `apikey_change_log` 相关的 SQL
  - 必要的测试或最小验证记录
- **具体改动**：
  - 增加 `apikey_change_log` 表结构
  - 确认字段足以覆盖 owner、manager、parentCode 的前后变化
  - 重点验证以下路径：
    - 父 AK 执行 owner/change
    - 子 AK 执行 owner/change
    - 子 AK 执行 parent/change
    - 父 AK 执行扁平化 parent/change
    - owner/transfer 同步写 change log
    - change/history 查询权限与内容

## 风险与依赖
- `owner_code` / `manager_code` 双轨编码仍然存在，若误用当前操作者规则重算 managerCode，会造成历史身份编码不一致
- `apikey_change_log` 若未建表，治理接口虽然代码完成但无法真正落地
- GitHub fork 当前存在一次 push 凭据报错，后续创建 plan PR 可能仍会受阻
- 后端测试依赖数据库，CLI 环境下无法完整验证事务与日志落库行为，需要结合本地数据库环境补充验证
- 若后续要把 `/owner/transfer` 完全收敛到 `apikey_change_log`，还需要单独评估旧表兼容与查询迁移策略

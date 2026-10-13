# Execution Plan for #837

feat: 在私有渠道配置中增加计费开关

## 目标

为私有渠道（visibility=private）增加一个可配置项 `billing_enabled`，允许在创建/编辑私有渠道时控制该渠道的请求是否走计费逻辑（费用计入 API Key 额度）。

当前系统中，私有渠道一律跳过计费（`CostLogHandler` 第 72 行：`if(log.isPrivate()) { return; }`）。本需求将该行为改为可配置：
- `billing_enabled = 0`（默认）：保持现有行为，私有渠道不计费
- `billing_enabled = 1`：私有渠道的请求也参与计费，费用计入对应 API Key 的额度

## 非目标

- 不修改公共渠道（visibility=public）的计费逻辑
- 不修改计费计算方式（CostCalculator 的定价和算法）
- 不涉及前端 UI 变更（本期仅后端 API + 数据库）
- 不修改 MonthQuotaInterceptor 的月度额度检查逻辑（该拦截器在路由前执行，尚不知道渠道信息）
- 不引入新的计费模式或计费公式

## 验收标准

1. channel 表新增 `billing_enabled` 字段（TINYINT，默认 0）
2. 创建/编辑私有渠道的 API 接口支持传入 `billingEnabled` 参数
3. 当 `billing_enabled = 1` 时，私有渠道请求正常执行费用计算并计入 API Key 额度
4. 当 `billing_enabled = 0`（或字段为 null）时，行为与当前一致（私有渠道不计费）
5. 对公共渠道无任何影响，公共渠道始终计费
6. 并发限流（`limiterManager.incrementConcurrentCount`）同步受 `billingEnabled` 控制
7. 日志记录中仍保留成本计算结果（`log.setCost`），只是不再 delta 计入 API Key

## 约束

- 数据库变更须向后兼容（新字段有默认值，不影响存量数据）
- jOOQ 代码需要重新生成（`mvn jooq:generate -pl server`）
- 不能因此功能引入额外的 Redis 查询或数据库查询（利用已路由到的 channel 信息）
- 需要维持 `isPrivate` 语义不变（仍用于路由优先级判断等场景），新增独立的 `isBillingSkipped` 语义

## 变更范围

### 数据库层
| 文件 | 变更 |
|------|------|
| `api/server/sql/` | 新增 migration SQL：ALTER TABLE channel ADD COLUMN `billing_enabled` TINYINT DEFAULT 0 |

### 数据模型层（jOOQ 生成）
| 文件 | 变更 |
|------|------|
| `api/server/src/codegen/java/.../tables/pojos/ChannelDB.java` | 重新生成，自动包含 `billingEnabled` 字段 |
| `api/server/src/codegen/java/.../tables/Channel.java` | 重新生成 |
| `api/server/src/codegen/java/.../tables/records/ChannelRecord.java` | 重新生成 |

### 核心逻辑层
| 文件 | 变更 |
|------|------|
| `api/sdk/.../EndpointProcessData.java` | 新增 `billingSkipped` 字段（替代直接依赖 `isPrivate`） |
| `api/server/.../EndpointContext.java` | `setEndpointData(ChannelDB)` 中根据 visibility + billing_enabled 设置 `billingSkipped` |
| `api/server/.../protocol/log/CostLogHandler.java` | 将 `if(log.isPrivate())` 改为 `if(log.isBillingSkipped())` |
| `api/server/.../protocol/log/LimiterLogHandler.java` | 将 `!log.isPrivate()` 改为 `!log.isBillingSkipped()` |

### Controller 层（并发限流判断）
| 文件 | 变更 |
|------|------|
| `api/server/.../endpoints/ChatController.java` | `isPrivate()` 改为 `isBillingSkipped()` |
| `api/server/.../endpoints/EmbeddingController.java` | 同上 |
| `api/server/.../endpoints/AudioController.java` | 同上 |
| `api/server/.../endpoints/ImagesController.java` | 同上 |
| `api/server/.../endpoints/GeminiController.java` | 同上 |
| `api/server/.../endpoints/MessageController.java` | 同上 |
| `api/server/.../endpoints/DocumentController.java` | 同上 |
| `api/server/.../endpoints/WebController.java` | 同上 |
| `api/server/.../protocol/ocr/OcrContext.java` | 同上 |
| `api/server/.../protocol/completion/QueueAdaptor.java` | 同上 |

### Console/管理 API 层
| 文件 | 变更 |
|------|------|
| `api/server/.../console/MetadataConsoleController.java` | 渠道创建/编辑接口支持 `billingEnabled` 参数 |
| `api/server/.../service/ChannelService.java` | 传递 `billingEnabled` 到持久层 |

## 实现思路

### Step 1: 数据库 Schema 变更

1. 在 `api/server/sql/` 下新增 migration SQL 文件：
   ```sql
   ALTER TABLE channel ADD COLUMN billing_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用计费(0:不计费,1:计费)';
   ```
2. 执行 `mvn jooq:generate -pl server` 重新生成 jOOQ 代码

**验证**：检查 `ChannelDB.java` 中包含 `billingEnabled` 字段

### Step 2: 扩展 EndpointProcessData

1. 在 `api/sdk/.../EndpointProcessData.java` 中新增字段：
   ```java
   private boolean billingSkipped;
   ```
2. 提供 getter/setter（Lombok 自动生成）

**验证**：编译 SDK 模块通过

### Step 3: 修改 EndpointContext 设置逻辑

在 `EndpointContext.setEndpointData(ChannelDB channel)` 中增加 billingSkipped 的设置逻辑：
```java
boolean isPrivate = EntityConstants.PRIVATE.equals(channel.getVisibility());
EndpointContext.getProcessData().setPrivate(isPrivate);
// billing_enabled=1 表示启用计费，即不跳过; 否则私有渠道跳过计费
boolean billingSkipped = isPrivate && (channel.getBillingEnabled() == null || channel.getBillingEnabled() == 0);
EndpointContext.getProcessData().setBillingSkipped(billingSkipped);
```

同样在 `QueueAdaptor` 的 `setEndpointData` 处增加同样逻辑。

**验证**：单元测试覆盖：private + billing_enabled=0 → billingSkipped=true, private + billing_enabled=1 → billingSkipped=false, public → billingSkipped=false

### Step 4: 修改计费跳过逻辑

1. `CostLogHandler.onEvent()`：将 `if(log.isPrivate()) { return; }` 改为 `if(log.isBillingSkipped()) { return; }`
2. `LimiterLogHandler`：将 `!log.isPrivate()` 改为 `!log.isBillingSkipped()`

**验证**：构建通过，CostLogHandler 行为变更有测试覆盖

### Step 5: 修改 Controller 层并发限流判断

将所有 Controller 中 `if(!processData.isPrivate())` 改为 `if(!processData.isBillingSkipped())`：
- ChatController、EmbeddingController、AudioController、ImagesController
- GeminiController、MessageController、DocumentController、WebController
- OcrContext、QueueAdaptor

**验证**：编译通过，grep 确认无遗漏的 `isPrivate()` 用于计费/限流判断的场景

### Step 6: Console API 支持配置

1. 在渠道创建/编辑的 DTO 或参数中增加 `billingEnabled` 字段
2. `ChannelService` 在持久化时写入 `billing_enabled` 列
3. 查询渠道详情时返回 `billingEnabled` 字段

**验证**：通过 API 创建私有渠道并设置 billing_enabled=1，确认数据正确入库

### Step 7: 集成验证

1. 创建私有渠道，billing_enabled=0，发送请求 → 不计费（行为不变）
2. 创建私有渠道，billing_enabled=1，发送请求 → 计费正常
3. 公共渠道 → 始终计费（回归验证）
4. 存量私有渠道（billing_enabled 为默认 0）→ 行为不变

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 存量私有渠道行为变更 | 若默认值设置错误可能导致已有私有渠道突然开始计费 | 默认值设为 0（不计费），与现有行为一致 |
| jOOQ 代码生成失败 | 需要连接数据库才能生成 | 可在开发环境执行后提交生成的代码 |
| `isPrivate` 语义被误改 | 影响路由优先级逻辑 | 保持 `isPrivate` 不变，新增 `billingSkipped` 独立字段 |
| 并发限流不一致 | 若 Controller 层遗漏修改 | 通过 grep 全面排查所有 `isPrivate()` 的计费相关使用 |
| MonthQuotaInterceptor 在路由前执行 | 私有渠道启用计费后，quota 检查在路由前已完成，不影响 | 无需修改，quota 检查只关心 akCode 的累计花费 |

### 依赖

- 数据库 migration 需在部署前执行
- jOOQ 代码生成需要可用的数据库连接

## 验证方式

1. **编译验证**：`mvn clean compile` 全模块编译通过
2. **单元测试**：新增测试覆盖 `billingSkipped` 的三种场景（private+billing_off, private+billing_on, public）
3. **集成测试**：通过 Console API 创建带 billingEnabled 配置的私有渠道，发送请求验证计费行为
4. **回归验证**：存量数据不受影响，公共渠道行为不变
5. **SQL 验证**：确认 ALTER TABLE 语句在干净数据库和已有数据的库上均可执行

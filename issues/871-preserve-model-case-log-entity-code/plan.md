# Execution Plan for #871

## 目标

1. 移除 Chat、Message、Responses endpoint 中对请求 model 字段的 `toLowerCase` 强制转换，保留原始大小写
2. 在 `EndpointProcessData` 中新增 `channelEntityCode` 字段，用于记录最终命中渠道的实体编码
3. 在 `EndpointContext.setCommonChannelData()` 中将 `ChannelDB.entityCode` 写入 `channelEntityCode`
4. 在 `EndpointLogger.log()` 中，当 `channelEntityCode` 非空时用其覆盖日志的 model 字段

## 非目标

- 不修改路由逻辑（ChannelRouter）中的模型匹配方式
- 不修改数据库表结构
- 不修改前端代码
- 不改变 API 对外响应格式
- 不处理 model 的 case-insensitive 路由匹配（若需要则作为独立 issue 处理）

## 验收标准

1. 请求 `model: "GPT-4o"` 不会被 endpoint 层改写为 `gpt-4o`
2. 多模型兜底逻辑中每个子模型保留原始值（不再 `.trim().toLowerCase()`）
3. `EndpointProcessData` 包含 `channelEntityCode` 字段，标记 `@JsonIgnore`
4. 渠道路由完成后 `channelEntityCode` 被正确设置
5. 日志写入时 `model` 字段优先使用 `channelEntityCode`（非空时覆盖）
6. 接口响应 JSON 中不暴露 `channelEntityCode` 字段

## 约束

- 路由层（ChannelRouter）当前以何种方式匹配 model 与渠道需保持不变；若路由依赖小写化匹配，则需要在路由层内部处理，而非在 endpoint 层修改入参
- `EndpointProcessData` 属于 sdk 模块，字段变更需考虑序列化兼容性（使用 `@JsonIgnore`）
- 本次修改不能破坏现有的按 model 维度统计（旧日志中 model 为小写，修改后日志中 model 变为 channelEntityCode，需确认统计系统兼容）

## 变更范围

| 文件 | 模块 | 变更类型 |
|------|------|----------|
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java` | server | 移除 L89 和 L117、L144 的 `toLowerCase` 调用 |
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/MessageController.java` | server | 移除 L65 的 `toLowerCase` 调用 |
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/ResponsesController.java` | server | 移除 L68 的 `toLowerCase` 调用 |
| `api/sdk/src/main/java/com/ke/bella/openapi/EndpointProcessData.java` | sdk | 新增 `channelEntityCode` 字段 + `@JsonIgnore` |
| `api/server/src/main/java/com/ke/bella/openapi/EndpointContext.java` | server | `setCommonChannelData()` 中写入 `channelEntityCode` |
| `api/server/src/main/java/com/ke/bella/openapi/protocol/log/EndpointLogger.java` | server | `log()` 中用 `channelEntityCode` 覆盖 model |

## 实现思路

### Step 1: 新增 `channelEntityCode` 字段

**文件**: `api/sdk/src/main/java/com/ke/bella/openapi/EndpointProcessData.java`

**操作**:
- 在现有字段区域（如 `channelCode` 之后）添加:
  ```java
  @JsonIgnore
  private String channelEntityCode;
  ```

**验证**: 编译 sdk 模块 `mvn compile -pl sdk`，确认无错误

### Step 2: 在渠道上下文中写入 channelEntityCode

**文件**: `api/server/src/main/java/com/ke/bella/openapi/EndpointContext.java`

**操作**:
- 在 `setCommonChannelData(ChannelDB channel)` 方法中添加:
  ```java
  processData.setChannelEntityCode(channel.getEntityCode());
  ```

**验证**: 编译 server 模块 `mvn compile -pl server`

### Step 3: 移除 ChatController 中的 model 小写化

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java`

**操作**:
- L89: 移除 `model = model.toLowerCase(Locale.ROOT);`，仅保留 `request.setModel(model);` 的逻辑（若 model 来自 request 本身则无需 setModel）
- L117: 多模型循环中将 `String trimmedModel = singleModel.trim().toLowerCase();` 改为 `String trimmedModel = singleModel.trim();`
- L144: `processCompletionRequest` 方法中移除 `EndpointContext.getProcessData().setModel(model.toLowerCase(Locale.ROOT));`，改为 `EndpointContext.getProcessData().setModel(model);`

**验证**: 编译通过 + 单元测试（若存在）

### Step 4: 移除 MessageController 中的 model 小写化

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/MessageController.java`

**操作**:
- L65: 移除 `model = model.toLowerCase(Locale.ROOT);`，保留 `request.setModel(model);`

**验证**: 编译通过

### Step 5: 移除 ResponsesController 中的 model 小写化

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/ResponsesController.java`

**操作**:
- L68: 移除 `model = model.toLowerCase(Locale.ROOT);`，保留 `request.setModel(model);`

**验证**: 编译通过

### Step 6: EndpointLogger 日志写入时覆盖 model

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/log/EndpointLogger.java`

**操作**:
- 在 `log(EndpointProcessData log)` 方法中，`log.setApikey(null);` 之后添加:
  ```java
  if (StringUtils.isNotBlank(log.getChannelEntityCode())) {
      log.setModel(log.getChannelEntityCode());
  }
  ```
- 需要导入 `org.apache.commons.lang3.StringUtils`

**验证**: 编译通过

### Step 7: 全量编译和验证

**操作**:
```bash
cd api/
mvn clean compile -DskipTests
```

**验证**: 全模块编译无错误

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 路由层依赖小写 model 做匹配 | 移除 toLowerCase 后路由可能找不到渠道 | 需确认 ChannelRouter.route() 是否做了 case-insensitive 匹配；若未做需在路由层内部补充 |
| 日志统计系统依赖小写 model | 旧日志和新日志 model 字段口径不一致 | 新日志使用 channelEntityCode（已存在的实体编码），应与渠道维度对齐；需与数据团队确认 |
| 多模型兜底移除 trim().toLowerCase() 后空格未处理 | 保留 `.trim()` 但不 toLowerCase | 代码仅移除 toLowerCase，保留 trim |
| EndpointProcessData 新字段序列化 | SDK 被外部引用时可能影响 JSON | `@JsonIgnore` 确保不进入序列化输出 |

## 验证方式

1. **编译验证**: `mvn clean compile` 全模块通过
2. **单元测试**: `mvn test -pl server`（若 CI 环境可用）
3. **手动验证**:
   - 发送请求 `model: "GPT-4o"`，确认响应中 model 未被改写
   - 查看日志输出中 model 字段是否为 channelEntityCode
   - 多模型请求 `model: "GPT-4o,Claude-3"` 确认每个子模型保留原始大小写
4. **回归测试**: 确认现有 lowercase model（如 `gpt-4o`）请求路由和日志不受影响

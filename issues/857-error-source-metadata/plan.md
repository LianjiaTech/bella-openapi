# Execution Plan for #857: 为 OpenAPI 错误响应增加错误来源元数据

## 目标

在 OpenAPI 错误响应中增加 `source` 和 `upstreamHttpCode` 字段，使调用方能够明确区分错误来自网关自身还是下游渠道，并在 Prometheus 指标中增加 `error_source` 标签以支持按错误来源聚合分析。

## 非目标

- 不修改现有错误码体系（`code`、`httpCode`、`type` 语义保持不变）
- 不引入新的异常类层次结构
- 不修改下游渠道的健康检测或重试逻辑
- 不变更前端展示逻辑
- 不修改已有 Grafana Dashboard 或告警规则（仅新增标签维度）

## 验收标准

1. `OpenapiResponse.OpenapiError` 新增 `source` 字段（`String`），取值范围：`gateway`、`channel`、`network`、`timeout`
2. `OpenapiResponse.OpenapiError` 新增 `upstreamHttpCode` 字段（`Integer`），序列化时为 null 则不输出
3. `ChannelException` 转换为 `OpenapiError` 时，`source=channel`，并填充 `upstreamHttpCode`
4. 网关侧异常（`RateLimitException`、`AuthorizationException`、`SafetyCheckException` 及通用 `BellaException`）转换时 `source=gateway`
5. 路由失败阶段（`failureStage=route`）错误归类为 `gateway`
6. 已选中渠道或已发生转发的错误默认归类为 `channel`
7. `bella_channel_requests` 指标新增 `error_source` 标签，取值：`none`（成功）、`gateway`、`channel`、`unknown`
8. 单元测试覆盖 channel/gateway 错误来源转换逻辑

## 约束

- `source` 字段使用 `@JsonInclude(JsonInclude.Include.NON_NULL)` 注解，成功响应不输出该字段
- `upstreamHttpCode` 字段使用 `@JsonInclude(JsonInclude.Include.NON_NULL)` 注解
- 新增标签不能导致 Prometheus 指标基数爆炸（取值有限枚举）
- 保持 `OpenapiError` 的序列化兼容性，新字段对旧客户端透明
- `ChannelException` 构造函数保持向后兼容，新增字段通过 setter 或新构造函数传入

## 变更范围

| 模块 | 文件 | 变更类型 |
|------|------|----------|
| sdk | `OpenapiResponse.java` | 新增 `source`、`upstreamHttpCode` 字段 |
| sdk | `BellaException.java` | 修改 `convertToOpenapiError()` 填充 `source`；`ChannelException` 填充 `upstreamHttpCode` |
| server | `EndpointResponseAdvice.java` | 在 `exceptionHandler` 中确保 `source` 已填充 |
| server | `ChatCompletionPrometheusRecorder.java` | `bella_channel_requests` 增加 `error_source` 标签 |
| server | 单元测试（新增） | 测试错误来源转换逻辑 |

## 实现思路

### Step 1: 在 `OpenapiError` 中新增字段

**文件**: `api/sdk/src/main/java/com/ke/bella/openapi/protocol/OpenapiResponse.java`

操作：
1. 在 `OpenapiError` 类中增加 `source` 字段（`String` 类型），加 `@JsonInclude(JsonInclude.Include.NON_NULL)` 注解
2. 在 `OpenapiError` 类中增加 `upstreamHttpCode` 字段（`Integer` 类型），加 `@JsonInclude(JsonInclude.Include.NON_NULL)` 注解
3. 定义 `ErrorSource` 常量接口或在 `OpenapiError` 内部定义常量：`SOURCE_GATEWAY = "gateway"`、`SOURCE_CHANNEL = "channel"`、`SOURCE_NETWORK = "network"`、`SOURCE_TIMEOUT = "timeout"`

验证：编译通过，现有测试不受影响。

### Step 2: 修改 `BellaException.convertToOpenapiError()` 填充 source

**文件**: `api/sdk/src/main/java/com/ke/bella/openapi/common/exception/BellaException.java`

操作：
1. 修改 `convertToOpenapiError()` 方法：
   - `ChannelException` 分支：设置 `source=channel`，并从 `ChannelException` 获取原始 httpCode 设置到 `upstreamHttpCode`
   - `SafetyCheckException` 分支：设置 `source=gateway`
   - 默认分支（其他 `BellaException`）：设置 `source=gateway`
2. 在 `ChannelException` 中保存原始的 upstream httpCode（当前构造函数已有 `httpCode` 参数，但会被截断为 503。需要新增 `upstreamHttpCode` 字段保存原始值）

验证：编译通过；通过单元测试验证不同异常类型生成的 `OpenapiError.source` 值正确。

### Step 3: 在 `EndpointResponseAdvice` 中补充 source

**文件**: `api/server/src/main/java/com/ke/bella/openapi/intercept/EndpointResponseAdvice.java`

操作：
1. 在 `exceptionHandler` 方法中，`convertToOpenapiError()` 已经在 Step 2 中填充了 source，无需额外处理
2. 在 `beforeBodyWrite` 中，对已有 error 但 `source` 为空的情况进行补充：
   - 如果 `EndpointContext.getProcessData().getFailureStage()` 为 `route`，设置 `source=gateway`
   - 如果 `channelCode` 不为空（说明已选中渠道），设置 `source=channel`
   - 其他情况设置 `source=gateway` 作为默认值

验证：手动测试或集成测试验证不同场景下 error response 包含正确的 source。

### Step 4: 在 Prometheus 指标中增加 `error_source` 标签

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/metrics/ChatCompletionPrometheusRecorder.java`

操作：
1. 在 `recordRouteFailure` 方法中，`bella_channel_requests` counter 增加 `"error_source", "gateway"` 标签
2. 在 `recordChannelUsage` 方法中，`bella_channel_requests` counter 增加 `"error_source"` 标签：
   - 无 error 时：`none`
   - 有 error 且 `source` 字段为 `channel`：`channel`
   - 有 error 且 `source` 字段为 `gateway`：`gateway`
   - 其他：`unknown`

验证：启动服务后通过 `/actuator/prometheus` 查看 `bella_channel_requests` 指标确认包含 `error_source` 标签。

### Step 5: 编写单元测试

**文件**: `api/server/src/test/java/com/ke/bella/openapi/ErrorSourceTest.java`（新增）

测试用例：
1. `ChannelException` 转 `OpenapiError` → `source=channel`，`upstreamHttpCode` 正确填充
2. `RateLimitException` 转 `OpenapiError` → `source=gateway`，`upstreamHttpCode=null`
3. `AuthorizationException` 转 `OpenapiError` → `source=gateway`
4. `SafetyCheckException` 转 `OpenapiError` → `source=gateway`
5. 通用 `BellaException.fromException(new IOException(...))` → `source=gateway`
6. `ChannelException` 原始 httpCode 为 500+ 时，`upstreamHttpCode` 保留原始值而非截断后的 503

验证：`mvn test -pl server -Dtest=ErrorSourceTest` 全部通过。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Prometheus 指标新增标签导致存量时间线中断 | 告警规则可能需要更新 | `error_source` 取值有限（4种），基数可控；提前通知 SRE 团队 |
| `ChannelException` 构造函数改动影响下游调用方 | 编译失败 | 通过新增字段+setter 方式，不修改现有构造函数签名 |
| `beforeBodyWrite` 中直接返回的 error（非异常路径）可能遗漏 source | 部分响应缺失 source | 在 `beforeBodyWrite` 中兜底补充 |
| 流式响应中的错误可能走不同路径 | 流式 error chunk 可能缺少 source | 检查流式错误处理路径，确保一致性 |

## 验证方式

1. **编译验证**：`mvn clean compile` 全模块通过
2. **单元测试**：`mvn test -pl server -Dtest=ErrorSourceTest` 通过
3. **集成验证**（手动）：
   - 发送无效 API Key 请求 → 响应包含 `source=gateway`
   - 发送请求到不可用渠道 → 响应包含 `source=channel` + `upstreamHttpCode`
   - 触发限流 → 响应包含 `source=gateway`
   - 查看 `/actuator/prometheus` → `bella_channel_requests` 包含 `error_source` 标签
4. **兼容性验证**：成功响应中 `source` 和 `upstreamHttpCode` 字段不出现在 JSON 中

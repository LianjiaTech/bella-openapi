# Execution Plan for #858

## 目标

修复 QPS 限流触发时 `EndpointResponseAdvice` 打印完整异常栈的问题。限流是正常业务行为，日志中只需记录 WARN 级别的消息文本，不应输出异常堆栈。

## 非目标

- 不修改限流逻辑本身（QPS 阈值、滑动窗口算法等）
- 不修改异常类型层次结构（`BellaException` 及其子类）
- 不调整其他类型异常（500、400、401）的日志行为
- 不涉及前端或配置变更

## 验收标准

1. QPS 超限时，日志仅输出 WARN 级别的一行消息（含 req_id 和限流原因），无异常栈
2. 月额度超限（同为 `RateLimitException`、HTTP 429）的日志行为与 QPS 超限一致：仅消息，无异常栈
3. 其他类型异常（500 级别错误、400/401 参数/认证错误）的日志行为不变
4. 单元测试验证 `logError` 在 HTTP 429 场景下不传递 throwable

## 约束

- 仅修改 `EndpointResponseAdvice.java` 中的 `logError` 方法
- 变更必须向后兼容，不改变 HTTP 响应格式或状态码
- 不引入新依赖

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/intercept/EndpointResponseAdvice.java` | 修改 | `logError` 方法中 HTTP 429 不传递异常对象 |

## 实现思路

### Step 1: 修改 `logError` 方法的日志分支逻辑

**文件**: `api/server/src/main/java/com/ke/bella/openapi/intercept/EndpointResponseAdvice.java`
**位置**: `logError` 方法（第 88-98 行）

**当前逻辑**:
```java
private void logError(Integer httpCode, String requestId, String msg, Throwable e) {
    String str = "req_id :" + requestId + ",msg:" + msg;
    if(httpCode == 500) {
        log.error(str, e);
    } else if(httpCode == 400 || httpCode == 401) {
        log.info(str, e);
    } else {
        log.warn(str, e);  // 429 走这里，打印了完整异常栈
    }
}
```

**修改后逻辑**:
```java
private void logError(Integer httpCode, String requestId, String msg, Throwable e) {
    String str = "req_id :" + requestId + ",msg:" + msg;
    if(httpCode == 500) {
        log.error(str, e);
    } else if(httpCode == 400 || httpCode == 401) {
        log.info(str);
    } else {
        log.warn(str);
    }
}
```

**原理**: HTTP 429（限流）和 400/401（参数/认证错误）都是客户端侧的预期行为，只需记录消息便于排查，无需异常栈。仅 500（服务端内部错误）需要完整异常栈辅助定位问题。

**验证方式**:
- 本地启动服务，使用压测工具触发 QPS 超限，观察日志输出仅有一行 WARN 消息
- 检查 400/401 场景日志同样无异常栈（回归验证）
- 制造一个 500 错误确认仍有完整异常栈输出

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 400/401 场景去掉异常栈后，排查特定参数异常时信息减少 | 低 | 400/401 通常是明确的参数校验或认证失败，消息文本已足够定位；若需要更多信息可临时调整日志级别到 DEBUG |
| 存在其他 HTTP 状态码（如 403）也走 else 分支 | 低 | 检查所有 `BellaException` 子类的 `getHttpCode()` 返回值，确认非 500 的异常都是业务预期行为 |

**依赖**: 无外部依赖，变更自包含。

## 验证方式

1. **代码审查**: 确认 `logError` 方法仅在 httpCode == 500 时传递 throwable
2. **集成验证**: 部署后观察生产日志，确认限流场景无异常栈输出
3. **回归验证**: 确认 500 错误仍输出完整异常栈，便于问题定位

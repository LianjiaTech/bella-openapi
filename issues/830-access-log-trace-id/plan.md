# Execution Plan for #830

> feat: 增强 API 网关 HTTP 访问日志并补全 SSE 连接 traceId

## 目标

1. 在 `OpenapiRequestFilter` 的请求入口打印包含 traceId、HTTP method、完整 URI 的访问日志。
2. 在 `OpenapiRequestFilter` 的响应出口打印包含 traceId、akCode、HTTP status、请求耗时的结果日志。
3. 在 `SseHelper#createSse` 的四个生命周期回调（创建成功、完成、超时、异常）中统一补充 traceId 字段。

## 非目标

- 不改动日志采集/存储/检索基础设施。
- 不新增 Metrics 或 Prometheus 指标。
- 不修改 `BellaRequestFilter`（父类，属于 sdk 模块）的现有逻辑。
- 不调整 traceId 的生成策略或格式。
- 不修改前端代码。

## 验收标准

1. 每个经过 `OpenapiRequestFilter` 的请求在日志中产生一条 Request 行和一条 Response 行。
2. Request 日志格式：`[traceId=xxx] Request  : GET /v1/chat/completions`。
3. Response 日志格式：`[traceId=xxx][akCode=xxx] Response : GET /v1/chat/completions status=200 cost=123ms`。
4. SSE 创建、完成、超时、异常四个日志行均包含 `[traceId=xxx]` 前缀。
5. 当 akCode 不可用时（如认证前的异常），Response 日志中 akCode 显示为空或 `-`。
6. 不引入新依赖，不影响现有功能。

## 约束

- Java 8 兼容。
- 使用 Slf4j `log.info` 级别（与现有 SseHelper 日志级别一致）。
- traceId 通过 `BellaContext.getTraceId()` 获取。
- akCode 通过 `EndpointContext.getProcessData().getAkCode()` 获取。
- 耗时计算使用 `System.currentTimeMillis()` 差值或复用已有 `DateTimeUtils.getCurrentMills()`。
- `OpenapiRequestFilter` 中需确保 traceId 在 `bellaRequestFilter()` 调用后即可用（当前已满足）。

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/intercept/OpenapiRequestFilter.java` | 修改 | 新增 Request/Response 日志，调整 try 块范围 |
| `api/server/src/main/java/com/ke/bella/openapi/utils/SseHelper.java` | 修改 | 四个生命周期日志补充 traceId 参数 |

## 实现思路

### Step 1: 修改 `OpenapiRequestFilter.java`

**目标**：新增请求入口日志和响应出口日志，确保日志在 finally 块中可靠输出。

**具体操作**：

1. 添加 Slf4j `@Slf4j` 注解（或声明 `private static final Logger log`）。
2. 将 `bellaRequestFilter(request, response)` 调用移至 try 块之前或 try 块最顶部（确保 traceId 已初始化后打印入口日志）。
3. 在 `bellaRequestFilter` 调用之后、`chain.doFilter` 之前，记录请求开始时间 `long startTime = System.currentTimeMillis()` 并打印入口日志：
   ```java
   log.info("[traceId={}] Request  : {} {}", BellaContext.getTraceId(), request.getMethod(), request.getRequestURI());
   ```
4. 在 `chain.doFilter` 之后（或 finally 块中），打印出口日志：
   ```java
   long cost = System.currentTimeMillis() - startTime;
   String akCode = EndpointContext.getProcessData().getAkCode();
   log.info("[traceId={}][akCode={}] Response : {} {} status={} cost={}ms",
       BellaContext.getTraceId(),
       akCode != null ? akCode : "-",
       request.getMethod(),
       request.getRequestURI(),
       response.getStatus(),
       cost);
   ```
5. 确保 Response 日志在 finally 块中、`clearAll()` 之前输出，以保证 BellaContext 数据仍可用。

**验证方式**：
- 启动应用，发送任意 API 请求，检查日志输出格式。
- 发送未认证请求，确认 akCode 显示为 `-`。
- 确认 traceId 在 Request 和 Response 日志间一致。

### Step 2: 修改 `SseHelper.java`

**目标**：在四个生命周期回调日志中统一追加 traceId。

**具体操作**：

1. 修改 `createSse` 方法签名，新增 `String traceId` 参数（或在方法内通过 `BellaContext.getTraceId()` 获取后用 final 变量捕获，因为回调在异步线程执行时 ThreadLocal 可能已清除）。

   **推荐方案**：在方法开头捕获 traceId 到 final 局部变量，因为 SSE 回调可能在不同线程执行：
   ```java
   public static SseEmitter createSse(long timeout, String reqId) {
       String traceId = BellaContext.getTraceId();
       // ...
   }
   ```

   **备选方案**：如果调用方已有 traceId，可将方法签名改为 `createSse(long timeout, String reqId, String traceId)`，但需同步修改所有调用方。

   **决策**：需检查 `createSse` 的调用时机——若在 Filter 线程内调用，ThreadLocal 可用；若存在异步调用场景，则需新增参数。先搜索调用方确认。

2. 修改四个日志行：
   ```java
   sse.onCompletion(() -> log.info("[traceId={}][{}] 结束连接...................", traceId, reqId));
   sse.onTimeout(() -> log.info("[traceId={}][{}] 连接超时...................", traceId, reqId));
   sse.onError(e -> log.info("[traceId={}][{}] 连接异常,{}", traceId, reqId, e.toString()));
   log.info("[traceId={}][{}] 创建sse连接成功！", traceId, reqId);
   ```

**验证方式**：
- 发送 streaming chat completion 请求，确认创建日志包含 traceId。
- 等待连接正常完成，确认 onCompletion 日志包含 traceId。
- 模拟超时场景（设置极短 timeout），确认 onTimeout 日志包含 traceId。

### Step 3: 确认 `SseHelper.createSse` 调用方的 traceId 传递方式

**目标**：确定是通过 ThreadLocal 捕获还是新增方法参数。

**具体操作**：

1. 搜索所有 `SseHelper.createSse` 调用点。
2. 确认调用均发生在请求线程内（BellaContext ThreadLocal 可用）。
3. 若确认在请求线程内调用，使用方案一（方法内捕获 ThreadLocal）。
4. 若存在异步调用场景，采用新增参数方案并同步修改调用方。

**验证方式**：
- Grep 所有调用点，确认调用上下文。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| SSE 回调在异步线程执行，ThreadLocal 已清除 | traceId 获取为 null | 在 `createSse` 方法内通过 final 局部变量提前捕获 traceId |
| Response 日志在 finally 中输出时 response.getStatus() 可能为默认值 200（异常时） | 日志中状态码不准确 | Spring MVC 在 chain.doFilter 完成后 response status 已设置，此处应该准确 |
| 高并发下日志量增加 | 日志存储压力增大 | 使用 info 级别，可通过日志配置降级为 debug；每请求仅增加 2 行日志 |
| akCode 在某些异常路径下为 null | 日志中 akCode 字段为空 | 使用三元运算符默认显示 `-` |

## 验证方式

1. **本地启动验证**：启动 Spring Boot 应用，通过 curl 发送请求，检查控制台日志输出。
2. **SSE 流式验证**：调用 streaming chat completion endpoint，观察 SSE 生命周期日志。
3. **异常路径验证**：发送无效 API key 请求，确认 Response 日志包含错误状态码且 akCode 为 `-`。
4. **编译验证**：`mvn clean compile` 确保无编译错误。
5. **单元测试**：现有测试通过（`mvn test -pl server`），不引入回归。

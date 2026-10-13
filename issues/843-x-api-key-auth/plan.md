# Execution Plan: 支持 x-api-key 请求头鉴权

**Issue**: #843  
**Author**: agentrix  
**Type**: feature  

---

## 目标

在鉴权拦截器中增加对 `x-api-key` 请求头的支持，作为全局 fallback 认证方式，使社区 AI 工具链（Anthropic SDK、LangChain、LiteLLM 等）能通过 `x-api-key` 头完成鉴权。

## 非目标

- 不修改现有 `Authorization: Bearer` 的行为或优先级
- 不修改 Gemini 协议专用的 `x-goog-api-key` 逻辑
- 不引入新的配置项或开关（`x-api-key` 支持默认启用）
- 不改动 API Key 的验证逻辑（`ApikeyService`）
- 不涉及前端变更

## 验收标准

1. 客户端仅发送 `x-api-key: <apikey>` 头时，鉴权成功，正常调用 API
2. 同时发送 `Authorization: Bearer <apikey>` 和 `x-api-key` 时，以 `Authorization` 为准
3. Gemini 路径同时发送 `x-goog-api-key` 和 `x-api-key` 时，以 `x-goog-api-key` 为准
4. 不发送任何认证头时，仍返回 `Authorization is empty` 错误
5. `x-api-key` 的值直接作为 apikey 使用（不带 `Bearer ` 前缀）
6. 现有所有测试继续通过

## 约束

- 仅修改 `AuthorizationInterceptor.java` 一个文件
- 优先级链严格为：`Authorization` > 协议特定 header > `x-api-key`
- 不引入新依赖

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/intercept/AuthorizationInterceptor.java` | 修改 | 在 fallback 链中增加 `x-api-key` 支持 |

## 实现思路

### Step 1: 修改 `preHandle` 方法中的 fallback 逻辑

**文件**: `api/server/src/main/java/com/ke/bella/openapi/intercept/AuthorizationInterceptor.java`  
**位置**: `preHandle` 方法中 `else` 分支（约第 43-60 行）

**当前逻辑**:
```
1. 读取 Authorization header
2. 若为空 → 读取协议特定 header（如 x-goog-api-key）
3. 若仍为空 → 抛出 AuthorizationException
```

**修改后逻辑**:
```
1. 读取 Authorization header
2. 若为空 → 读取协议特定 header（如 x-goog-api-key）
3. 若仍为空 → 读取 x-api-key header
4. 若仍为空 → 抛出 AuthorizationException
```

**具体改动**: 在抛出 `AuthorizationException` 之前，增加对 `x-api-key` 的读取：

```java
// 如果为空，检查协议特定的备选 header
if(StringUtils.isEmpty(auth)) {
    String alternativeHeader = getAlternativeHeader(request.getRequestURI());
    if(alternativeHeader != null) {
        auth = request.getHeader(alternativeHeader);
    }
    // 协议特定 header 也为空，检查全局 fallback: x-api-key
    if(StringUtils.isEmpty(auth)) {
        auth = request.getHeader("x-api-key");
    }
    // 所有方式都为空，抛出异常
    if(StringUtils.isEmpty(auth)) {
        throw new BellaException.AuthorizationException("Authorization is empty");
    }
}
```

**验证方式**: 
- 单元测试模拟仅携带 `x-api-key` 头的请求，验证鉴权通过
- 单元测试模拟同时携带 `Authorization` 和 `x-api-key` 头，验证优先使用 `Authorization`
- 运行现有测试套件确认无回归

### Step 2: 验证与测试

- 检查现有测试类（如 `AuthorizationInterceptorTest` 或集成测试）是否覆盖 fallback 场景
- 如有现有测试文件，补充以下测试用例：
  - 仅 `x-api-key` 头 → 鉴权成功
  - `Authorization` + `x-api-key` 同时存在 → 使用 `Authorization`
  - Gemini 路径 `x-goog-api-key` + `x-api-key` 同时存在 → 使用 `x-goog-api-key`
  - 无任何认证头 → 返回 401

## 风险与依赖

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| `x-api-key` 值可能带 `Bearer ` 前缀 | 低 | `verifyAuth` 方法内部已处理前缀剥离，无需额外处理 |
| 与其他拦截器或过滤器冲突 | 低 | 仅在现有逻辑基础上增加一层 fallback，不改变执行流程 |
| 安全风险（扩大认证方式） | 低 | `x-api-key` 使用与 `Authorization` 完全相同的验证链（`apikeyService.verifyAuth`），安全等级一致 |

**依赖**: 无外部依赖，仅修改已有拦截器逻辑。

## 验证方式

1. **本地验证**: 使用 curl 模拟请求
   ```bash
   # x-api-key 鉴权
   curl -H "x-api-key: <valid-key>" http://localhost:8080/v1/chat/completions
   
   # 优先级验证：Authorization 优先
   curl -H "Authorization: Bearer <key-a>" -H "x-api-key: <key-b>" http://localhost:8080/v1/chat/completions
   # 应使用 key-a
   ```

2. **自动化测试**: 运行现有后端测试套件确认无回归
   ```bash
   cd api/ && mvn test -pl server
   ```

3. **集成验证**: 使用 Anthropic SDK 或 LangChain 配置 `x-api-key` 方式连接网关，验证端到端调用成功

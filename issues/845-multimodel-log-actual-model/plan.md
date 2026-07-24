# Execution Plan for #845

## 目标

多模型请求场景下，`EndpointProcessData.model` 应记录本次实际调用成功的单个模型，而非用户传入的逗号拼接模型列表。

## 非目标

- 不修改多模型路由/重试逻辑本身
- 不修改限流、安全检查或响应逻辑
- 不修改单模型请求的行为
- 不修改 `EndpointLogger` 或其他下游日志消费方
- 不调整 `setEndpointData` 的通用接口签名

## 验收标准

1. 单模型请求日志中 `model` 字段保持为该模型（行为不变）
2. 多模型请求成功后，日志中 `model` 为实际成功处理的模型
3. 多模型重试过程中，每次尝试都使用当前单个模型更新 `EndpointProcessData.model`
4. 不影响原有路由、限流、安全检查和响应逻辑

## 约束

- 修改范围仅限于 `ChatController.java` 中 `processCompletionRequest` 方法的入口处
- `EndpointProcessData` 类已有 `setModel` 方法（Lombok `@Data` 生成），无需新增 API
- 修改必须线程安全：`EndpointContext` 使用 ThreadLocal 隔离，同一请求线程内修改安全

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java` | 修改 | 在 `processCompletionRequest` 方法入口处添加 `processData.setModel(model)` |

## 实现思路

### Step 1: 在 `processCompletionRequest` 方法入口处更新 model

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java`
**位置**: `processCompletionRequest` 方法体，第 144 行 `EndpointProcessData processData = EndpointContext.getProcessData();` 之后

**操作**: 在获取 `processData` 之后、业务逻辑开始之前，添加一行：

```java
processData.setModel(model);
```

**完整上下文**:

```java
private Object processCompletionRequest(String endpoint, String model, CompletionRequest request) {
    boolean isMock = EndpointContext.getProcessData().isMock();

    // Initialize channel using common method
    ChannelContext ctx = initializeChannel(endpoint, model, false);
    CompletionProperty property = ctx.property;

    EndpointProcessData processData = EndpointContext.getProcessData();
    processData.setModel(model);  // <-- 新增：确保记录实际调用的单个模型
    if(!processData.isPrivate()) {
        limiterManager.incrementConcurrentCount(processData.getAkCode(), model);
    }
    // ... 后续逻辑不变
}
```

**原理**: `processCompletionRequest` 是单模型和多模型请求的统一处理入口。多模型场景下，外层循环每次迭代都会调用此方法并传入当前尝试的 `trimmedModel`。在此处覆盖 `processData.model` 即可保证：
- 单模型请求：model 值与初始 `setEndpointData` 设置的相同，行为无变化
- 多模型请求：model 值被更新为当前实际尝试/成功的单个模型

### Step 2: 验证

**验证方式**:
1. 代码审查：确认修改仅添加一行 `processData.setModel(model)`
2. 逻辑验证：
   - 单模型场景：`setEndpointData` 设置 model="model-a"，`processCompletionRequest` 再次设置 model="model-a"，无影响
   - 多模型场景：`setEndpointData` 设置 model="model-a,model-b,model-c"，循环第一次 `processCompletionRequest` 设置 model="model-a"，若失败，第二次设置 model="model-b"，最终日志记录成功的模型
3. 线程安全：`EndpointContext` 基于 ThreadLocal，同线程内对 processData 的修改不存在竞态
4. 集成测试（需数据库）：使用多模型请求，验证日志输出中 model 字段为实际调用模型

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 多模型全部失败时，model 字段为最后尝试的模型而非原始列表 | 低 - 失败日志也应记录最后尝试的模型，更有助于排查 | 行为合理，不需额外处理 |
| 其他代码路径依赖 `processData.model` 为逗号列表 | 低 - 经搜索未发现此类依赖 | 已通过全局搜索确认无此依赖 |

## 验证方式

1. **代码 review**: 确认变更为单行 `processData.setModel(model)` 添加
2. **单元测试**（实现 MR 中补充）: mock `EndpointContext`，验证多模型场景下 processData.model 被正确覆盖
3. **集成验证**: 发送 `model: "model-a,model-b"` 请求，检查日志和 Prometheus 指标中 model 维度是否为实际调用模型

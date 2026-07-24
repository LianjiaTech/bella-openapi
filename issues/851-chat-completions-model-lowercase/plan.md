# Execution Plan for #851

## 目标

修复 `ChatController.completion()` 中单模型请求路径的模型名大小写归一化不完整问题，确保：
1. 单模型请求在路由前统一对模型名执行 `toLowerCase(Locale.ROOT)`
2. 同步更新 `request.setModel(model)` 确保 adaptor 层也使用归一化后的模型名
3. 与多模型请求和其他 endpoint（MessageController、ResponsesController）的归一化策略保持一致

## 非目标

- 不修改 direct mode 行为（direct mode 透传原始 body，不做归一化）
- 不修改多模型请求路径（已正确实现 `trim().toLowerCase()`）
- 不修改其他 endpoint controller（#849 已处理 MessageController 和 ResponsesController）
- 不引入全局 model name 归一化中间件或拦截器
- 不处理 model name 的 trim()（当前单模型无空格问题，但可一并处理以保持一致性）

## 验收标准

1. 请求 `model=Deepseek-chat` 时能正确路由到 `deepseek-chat` 对应渠道
2. 请求 `model=deepseek-chat` 行为保持不变（幂等性）
3. 多模型请求行为保持不变
4. `request.getModel()` 在进入 `processCompletionRequest()` 后返回归一化后的值，确保下游 adaptor 不会收到混合大小写的模型名
5. direct mode 行为保持透明透传，不受归一化影响
6. `EndpointContext.getProcessData().getModel()` 与 `request.getModel()` 一致

## 约束

- 仅修改 `ChatController.java` 单模型路径相关代码
- 归一化使用 `Locale.ROOT`（与 #849 的实现保持一致）
- 不改变方法签名或公共接口
- 变更需对 direct mode 完全透明（direct mode 在归一化之前已提前 return）

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java` | 修改 | 在单模型路径增加 `toLowerCase(Locale.ROOT)` 和 `request.setModel(model)` |

## 实现思路

### Step 1: 在单模型路径增加归一化逻辑

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java`

**位置**: `completion()` 方法中，第 85 行获取 model 之后、第 88 行 `endpointDataService.setEndpointData()` 之前

**变更内容**:

```java
// 现有代码（第 85 行）
String model = isDirectMode ? BellaContext.getDirectModel() : request.getModel();

// 新增：对非 direct mode 的模型名进行归一化（与多模型路径和其他 endpoint 保持一致）
if (!isDirectMode && model != null) {
    model = model.toLowerCase(Locale.ROOT);
    request.setModel(model);
}

// 现有代码（第 88 行）
endpointDataService.setEndpointData(endpoint, model, request);
```

**说明**:
- 条件判断 `!isDirectMode` 确保 direct mode 不受影响
- `request.setModel(model)` 同步更新请求体，避免 adaptor 层读取到原始大小写
- 使用 `Locale.ROOT` 与 #849 的 MessageController/ResponsesController 实现保持一致
- 归一化在 `setEndpointData` 之前执行，确保 EndpointContext 也存储归一化后的值

### Step 2: 添加 `java.util.Locale` import

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java`

**变更内容**: 在 import 区域添加 `import java.util.Locale;`

### Step 3: 移除 `processCompletionRequest` 中的冗余 toLowerCase

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java`

**位置**: `processCompletionRequest()` 方法第 138 行

**变更内容**:

```java
// 现有代码
EndpointContext.getProcessData().setModel(model.toLowerCase());

// 改为（model 已在入口处归一化，此处保持防御性调用但使用 Locale.ROOT）
EndpointContext.getProcessData().setModel(model.toLowerCase(Locale.ROOT));
```

**说明**: 保留 `toLowerCase` 调用作为防御性编程（因为 `processCompletionRequest` 也被多模型路径调用），但统一使用 `Locale.ROOT`。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| adaptor 依赖原始大小写模型名作为 key 或参数 | 下游请求可能失败 | 代码审查确认所有 adaptor 都使用 channel 配置的 deployName 而非 request.model |
| `endpointDataService.setEndpointData` 内部逻辑依赖原始模型名 | 数据不一致 | 审查 EndpointContext.setEndpointData 实现，确认其使用传入的 model 参数 |
| 多模型路径中 `trimmedModel` 未使用 `Locale.ROOT` | 与新代码行为微差（对纯 ASCII 模型名无影响） | 本次不修改多模型路径以避免范围扩大，但建议后续统一 |

**依赖**: 无外部依赖。变更完全在 ChatController 内部完成。

## 验证方式

1. **代码审查**: 确认归一化逻辑位置正确，不影响 direct mode 和多模型路径
2. **单元测试**（如环境可用）:
   - 请求 `model=Deepseek-chat`，验证路由使用 `deepseek-chat`
   - 请求 `model=DEEPSEEK-CHAT`，验证路由使用 `deepseek-chat`
   - 请求 `model=deepseek-chat`，验证行为不变
   - 请求 `model=Model-A,Model-B`（多模型），验证行为不变
   - direct mode 请求，验证模型名保持原始值
3. **集成验证**: 部署后使用 `curl` 发送大小写混合的模型名请求，确认返回正常响应而非 404
4. **回归确认**: 确认现有正常请求（小写模型名）行为不变

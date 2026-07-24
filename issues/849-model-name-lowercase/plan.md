# Execution Plan for #849

## 目标

在 `MessageController` 和 `ResponsesController` 入口处统一将用户传入的 `model` 字段转为小写（`Locale.ROOT`），确保路由、日志、指标统计和下游 adaptor 使用一致的模型名。

## 非目标

- 不修改数据库中已存储的模型名数据
- 不对其他 Controller（如 `EmbeddingController`、`AsrController` 等）做改动
- 不引入配置开关或可选行为——一律转小写
- 不修改前端代码或管理端展示逻辑

## 验收标准

1. `MessageController`：传入 `GPT-4O`、`Gpt-4o`、`gpt-4o` 等不同大小写，`endpointDataService`、路由、下游 `request.getModel()` 均为 `gpt-4o`
2. `ResponsesController`：同上行为一致
3. 空 model 或 null model 保持原有校验逻辑（`MessageController` 允许 null 传递，`ResponsesController` 抛出 `BizParamCheckException`）
4. 已有小写模型名请求行为无变化（幂等）
5. 不影响其他 endpoint 的正常工作

## 约束

- 仅修改 `MessageController.java` 和 `ResponsesController.java` 两个文件
- 使用 `Locale.ROOT` 避免土耳其语等 locale 问题
- 不新增依赖
- 变更需前后兼容，无需数据迁移

## 变更范围

| 文件 | 变更描述 |
|------|----------|
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/MessageController.java` | 第 50 行后增加 null 判断和 `toLowerCase(Locale.ROOT)` + `request.setModel(model)` |
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/ResponsesController.java` | 第 62–65 行 blank 校验后增加 `toLowerCase(Locale.ROOT)` + `request.setModel(model)` |

## 实现思路

### Step 1: 修改 MessageController

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/MessageController.java`  
**位置**: `message()` 方法，第 50 行附近

将：
```java
String model = request.getModel();
endpointDataService.setEndpointData(endpoint, model, request);
```

改为：
```java
String model = request.getModel();
if (model != null) {
    model = model.toLowerCase(Locale.ROOT);
    request.setModel(model);
}
endpointDataService.setEndpointData(endpoint, model, request);
```

**验证**: 确认 `Locale` import 已存在或添加 `import java.util.Locale;`

### Step 2: 修改 ResponsesController

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/ResponsesController.java`  
**位置**: `createResponse()` 方法，第 62–67 行附近

将：
```java
String model = request.getModel();
if (StringUtils.isBlank(model)) {
    throw new BizParamCheckException("model is required");
}

endpointDataService.setEndpointData(endpoint, model, request);
```

改为：
```java
String model = request.getModel();
if (StringUtils.isBlank(model)) {
    throw new BizParamCheckException("model is required");
}
model = model.toLowerCase(Locale.ROOT);
request.setModel(model);

endpointDataService.setEndpointData(endpoint, model, request);
```

**验证**: 确认 `Locale` import 已存在或添加 `import java.util.Locale;`

### Step 3: 验证编译通过

```bash
cd api/ && mvn clean compile -pl server -am
```

## 风险与依赖

| 风险 | 说明 | 缓解措施 |
|------|------|----------|
| 下游系统依赖原始大小写 | 若下游 adaptor 或日志系统期望保留用户原始输入 | Issue 明确要求统一小写；如有需要可在后续保留原始值到 context |
| 数据库模型名未统一 | DB 中 model 字段若存有大写，路由 lookup 可能不匹配 | 路由使用 `ModelDB` 查找时应已统一（需确认），本次只改入口层 |
| 其他入口未覆盖 | 如 `EmbeddingController`、`RouteController` 等也使用 `request.getModel()` | 本 Issue 范围仅限 Messages/Responses，其他接口如需统一应另开 Issue |

## 验证方式

1. **编译验证**: `mvn clean compile -pl server -am` 通过
2. **手动接口测试**（开发环境）:
   - 向 `/v1/chat/completions` 发送 `{"model": "GPT-4O", ...}`，检查日志中 model 是否为 `gpt-4o`
   - 向 `/v1/responses` 发送 `{"model": "GPT-4O", ...}`，检查同上
   - 发送 model 为 null 的请求，确认原有行为不变
3. **单元测试**（可选追加）: 调用 controller 方法传入混合大小写 model，断言 `request.getModel()` 返回小写

# Execution Plan for #839

> fixbug: 修复 Responses API 未被识别为系统基础 Endpoint 的问题

## 目标

在 `EntityConstants.SystemBasicEndpoint` 枚举中新增 `RESPONSES_ENDPOINT("/v*/responses", "智能问答", CHAT)`，使 Responses API 路径被系统识别为基础 Endpoint，归类到 CHAT 类别。

## 非目标

- 不涉及 Responses API 的协议适配器实现或路由逻辑修改
- 不涉及前端页面变更
- 不涉及数据库 schema 变更
- 不修改其他已有 Endpoint 配置

## 验收标准

1. `SystemBasicEndpoint` 枚举中包含 `RESPONSES_ENDPOINT("/v*/responses", "智能问答", CHAT)`
2. `/v1/responses` 路径能匹配该 Endpoint 配置（通配符 `/v*/responses` 覆盖）
3. 该 Endpoint 分类为 `CHAT`
4. 该 Endpoint 展示名称为「智能问答」

## 约束

- 仅修改一个文件：`api/sdk/src/main/java/com/ke/bella/openapi/common/EntityConstants.java`
- 枚举值插入位置应在 `COMPLETION_ENDPOINT` 之后、`MESSAGES_ENDPOINT` 之前（与 Issue 描述一致）
- 不引入新依赖

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/sdk/src/main/java/com/ke/bella/openapi/common/EntityConstants.java` | 修改 | 在 `SystemBasicEndpoint` 枚举中新增 `RESPONSES_ENDPOINT` |

## 实现思路

### Step 1: 新增枚举值

**操作**：在 `api/sdk/src/main/java/com/ke/bella/openapi/common/EntityConstants.java` 文件中，`SystemBasicEndpoint` 枚举的 `COMPLETION_ENDPOINT` 之后添加一行：

```java
RESPONSES_ENDPOINT("/v*/responses", "智能问答", CHAT),
```

修改后枚举顺序为：
```java
public enum SystemBasicEndpoint {
    COMPLETION_ENDPOINT("/v*/chat/completions", "智能问答", CHAT),
    RESPONSES_ENDPOINT("/v*/responses", "智能问答", CHAT),
    MESSAGES_ENDPOINT("/v*/messages", "智能问答", CHAT),
    EMBEDDING_ENDPOINT("/v*/embeddings", "向量化", CHAT),
    SPEECH_ENDPOINT("/v*/audio/speech", "语音合成", TEXT2SPEECH),
    ASR_ENDPOINT("/v*/audio/transcriptions", "语音识别", AUDIO2TEXT),
    ...
}
```

**验证**：
- `mvn clean compile -pl sdk` 编译通过
- 确认无其他文件引用了 `SystemBasicEndpoint` 枚举的序号或顺序（枚举是按 name 使用的，非 ordinal）

### Step 2: 编译验证

**操作**：执行 `cd api && mvn clean compile` 确保全模块编译通过。

**验证**：构建成功，无编译错误。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 枚举 ordinal 变化 | 如果有代码依赖 `ordinal()` 值可能受影响 | 检查代码中是否存在对该枚举的 `ordinal()` 调用 |
| 路径通配符冲突 | `/v*/responses` 可能与其他路径匹配逻辑冲突 | 确认现有路径匹配逻辑为前缀/glob 匹配，无冲突 |

**依赖**：无外部依赖，修改自包含。

## 验证方式

1. **编译验证**：`cd api && mvn clean compile` 通过
2. **静态检查**：grep 确认新枚举值存在且格式正确
3. **引用检查**：确认无代码使用 `SystemBasicEndpoint.ordinal()`
4. **单元测试**（如有）：运行 `mvn test -pl sdk` 确认无回归

# Execution Plan for #868

## 目标

为 Claude 新模型实现 thinking 模式兼容转换：当 channel 配置 `thinkingModePolicy=adaptive_required` 时，在请求出站前将 `thinking.type=enabled`（manual thinking）自动转换为 `thinking.type=adaptive` + `output_config.effort`，避免新模型返回 400 错误。

## 非目标

- 不修改 direct passthrough（直通模式原始 body 直接转发，不做拦截）
- 不处理非 Claude 通道的 thinking 行为
- 不修改前端 UI 或 playground
- 不变更数据库 schema（新字段为 channelInfo JSON 内的配置项）
- 不做 `reasoning_effort` → `output_config.effort` 的全局归一化（本期仅处理 manual→adaptive 转换）

## 验收标准

1. `CompletionProperty` 和 `AwsMessageProperty` 新增 `thinkingModePolicy` 和 `defaultThinkingEffort` 字段，并在 `description()` 中暴露配置说明
2. 实现 `ClaudeThinkingNormalizer.normalize(MessageRequest, property)` 工具方法：
   - 当 `thinkingModePolicy=adaptive_required` 且 `thinking.type=enabled` 时，转换为 `thinking.type=adaptive`
   - 设置 `output_config.effort`：优先保留请求中已有值，其次使用 `defaultThinkingEffort`，最终兜底 `medium`
   - 当 `thinkingModePolicy` 未配置或为 `manual` 时，不做任何转换
3. 在 `AwsMessageAdaptor`（createMessages/streamMessages）和 `AnthropicAdaptor`（createMessages/streamMessages）的序列化前调用 normalizer
4. `AwsCompletionConverter.convertThinking` 在 `thinkingModePolicy=adaptive_required` 时，生成 adaptive thinking 而非 manual thinking
5. 单元测试覆盖：manual→adaptive 转换、已有 effort 保留、无 thinking 不变、policy=manual 不转换

## 约束

- channel 配置为 JSON 存储在 channelInfo 字段中，新增字段不需要数据库 DDL 变更
- 保持向后兼容：未配置 `thinkingModePolicy` 的 channel 行为不变
- `ThinkingConfig` 已有 `type` 字段支持 `enabled/disabled/adaptive`，`OutputConfig` 已有 `effort` 字段——无需新增 DTO
- `AwsCompletionConverter` 当前不持有 property 对象，需要调整方法签名或传递 policy 参数

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/sdk/.../completion/CompletionProperty.java` | 修改 | 新增 `thinkingModePolicy`、`defaultThinkingEffort` 字段及 description |
| `api/server/.../message/AwsMessageProperty.java` | 修改 | 同上（如 AWS 使用独立 property；若共用 CompletionProperty 则不需要） |
| `api/sdk/.../message/ClaudeThinkingNormalizer.java` | 新增 | 统一 thinking 模式转换工具类 |
| `api/server/.../message/AwsMessageAdaptor.java` | 修改 | 在 createMessages/streamMessages 序列化前调用 normalizer |
| `api/server/.../message/AnthropicAdaptor.java` | 修改 | 在 createMessages/streamMessages 序列化前调用 normalizer |
| `api/server/.../completion/AwsCompletionConverter.java` | 修改 | `convertThinking` 方法增加 policy 参数，支持生成 adaptive thinking |
| `api/sdk/src/test/...` 或 `api/server/src/test/...` | 新增 | 单元测试 |

## 实现思路

### Step 1: 扩展 CompletionProperty 配置字段

**文件**: `api/sdk/src/main/java/com/ke/bella/openapi/protocol/completion/CompletionProperty.java`

**操作**:
- 添加字段 `String thinkingModePolicy`（可选值: `manual`, `adaptive_required`）
- 添加字段 `String defaultThinkingEffort`（可选值: `low`, `medium`, `high`, `xhigh`, `max`）
- 在 `description()` 方法中添加对应说明

**验证**: 编译通过；检查 `AwsMessageProperty` 是否继承/包含 CompletionProperty，若是独立 class 则同步修改。

### Step 2: 实现 ClaudeThinkingNormalizer

**文件**: `api/sdk/src/main/java/com/ke/bella/openapi/protocol/message/ClaudeThinkingNormalizer.java`（新建）

**操作**:
```java
public class ClaudeThinkingNormalizer {
    public static void normalize(MessageRequest request, String thinkingModePolicy, String defaultThinkingEffort) {
        if (!"adaptive_required".equals(thinkingModePolicy)) return;
        ThinkingConfig thinking = request.getThinking();
        if (thinking == null || !thinking.isEnabled()) return;
        // 转换为 adaptive
        thinking.setType("adaptive");
        thinking.setBudgetTokens(null);
        // 设置 output_config.effort
        OutputConfig outputConfig = request.getOutputConfig();
        if (outputConfig == null || outputConfig.getEffort() == null) {
            String effort = defaultThinkingEffort != null ? defaultThinkingEffort : "medium";
            if (outputConfig == null) {
                request.setOutputConfig(OutputConfig.builder().effort(effort).build());
            } else {
                outputConfig.setEffort(effort);
            }
        }
    }
}
```

**验证**: 编写单元测试 `ClaudeThinkingNormalizerTest`：
- 输入 `thinking.type=enabled, budgetTokens=4000`，policy=`adaptive_required` → 输出 `type=adaptive, budgetTokens=null, effort=medium`
- 输入已有 `output_config.effort=high` → 保留 `high`
- 输入 `thinking=null` → 无变化
- 输入 policy=`manual` → 无变化
- 输入 policy=`null` → 无变化

### Step 3: 在 AwsMessageAdaptor 中调用 normalizer

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/message/AwsMessageAdaptor.java`

**操作**:
- 在 `createMessages` 方法中，`request.setAnthropic_version(...)` 之后、序列化之前插入:
  ```java
  ClaudeThinkingNormalizer.normalize(request, property.getThinkingModePolicy(), property.getDefaultThinkingEffort());
  ```
- 在 `streamMessages` 方法中同位置插入相同调用

**验证**: 编译通过；确认 property 类型包含新字段的 getter。

### Step 4: 在 AnthropicAdaptor 中调用 normalizer

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/message/AnthropicAdaptor.java`

**操作**:
- 在 `createMessages` 和 `streamMessages` 的序列化前调用:
  ```java
  ClaudeThinkingNormalizer.normalize(request, property.getThinkingModePolicy(), property.getDefaultThinkingEffort());
  ```
- 确认 AnthropicAdaptor 使用的 property 类型（可能是 `CompletionProperty` 或其子类）

**验证**: 编译通过。

### Step 5: 修改 AwsCompletionConverter.convertThinking

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/completion/AwsCompletionConverter.java`

**操作**:
- 修改 `convertThinking` 方法签名，增加 `String thinkingModePolicy` 和 `String defaultThinkingEffort` 参数
- 当 `thinkingModePolicy=adaptive_required` 时，生成 adaptive thinking config：
  ```java
  if ("adaptive_required".equals(thinkingModePolicy)) {
      thinking.put("thinking", ThinkingConfig.builder().type("adaptive").build());
      // 设置 effort...
  } else {
      thinking.put("thinking", ThinkingConfig.enabled(thinkingToken));
  }
  ```
- 更新所有调用 `convertThinking` 的地方传入 policy 参数

**验证**: 编译通过；追踪 `convertThinking` 的调用方确保参数传递正确。

### Step 6: 编写集成验证测试

**文件**: `api/server/src/test/java/com/ke/bella/openapi/protocol/message/ClaudeThinkingNormalizerTest.java`（新建）

**操作**:
- 编写纯单元测试（不依赖数据库），覆盖 Step 2 中列出的所有场景
- 针对 `AwsCompletionConverter.convertThinking` 新行为编写测试

**验证**: `mvn test -Dtest=ClaudeThinkingNormalizerTest` 通过。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `AwsMessageProperty` 与 `CompletionProperty` 可能是独立类 | 需要在两处添加字段 | 实现前确认继承关系 |
| `AwsCompletionConverter.convertThinking` 是 static 方法，修改签名影响调用方 | 编译错误 | 追踪所有调用方同步修改 |
| 已有 `ThinkingConfig` 无 adaptive 工厂方法 | 代码冗余 | 可选添加 `ThinkingConfig.adaptive()` 工厂方法 |
| 线上 channel 未配置新字段 | 不会触发转换 | 默认 policy=null 时不转换，保持向后兼容 |
| `OutputConfig` 上已有 `format` 字段，转换时不应覆盖 | 数据丢失 | normalize 方法中保留已有 OutputConfig 对象，只设置 effort |

## 验证方式

1. **编译验证**: `cd api && mvn clean compile` 无错误
2. **单元测试**: `mvn test -Dtest=ClaudeThinkingNormalizerTest` 全部通过
3. **配置验证**: 确认 `CompletionProperty.description()` 返回新字段说明
4. **回归验证**: 未配置 `thinkingModePolicy` 的 channel 请求行为不变（normalizer 直接 return）
5. **端到端验证**（部署后）: 配置一个 `thinkingModePolicy=adaptive_required` 的 Claude channel，发送带 `thinking.type=enabled` 的请求，确认下游收到 `thinking.type=adaptive`

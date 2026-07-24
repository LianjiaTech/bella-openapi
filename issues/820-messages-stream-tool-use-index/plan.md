# Execution Plan for #820

## 目标

修复 OpenAI 协议流式响应转换为 Anthropic `/v1/messages` SSE 事件时，`tool_use` content block 的 `index` 在 `content_block_start`、`content_block_delta`、`content_block_stop` 之间不一致的问题，使 ClaudeCode 不再因协议错误断开连接。

## 非目标

- 不重构 `StreamMessagesCallback` 的整体架构
- 不修改非 `tool_use` 相关的 content block index 逻辑（thinking、text 阶段）
- 不处理 `SseHelper.send()` 的 Broken pipe 异常捕获（那是 index 错乱的下游症状）
- 不修改非流式 `/v1/messages` 响应转换逻辑

## 验收标准

1. 同一个 tool_use content block 的 `content_block_start`、所有 `content_block_delta`（input_json_delta）、`content_block_stop` 的 index 值完全一致
2. 多个连续 tool_use block 的 index 严格递增（前一个 stop 后，下一个 start index = 前一个 + 1）
3. text → tool_use 阶段切换时，先发出 text block 的 `content_block_stop`，再发出 tool_use 的 `content_block_start`，index 递增正确
4. 只含 `finish_reason` 或空 delta 的 chunk 不会误触发新 content block
5. ClaudeCode 通过 `/v1/messages` stream 请求 Deepseek-V4-Pro 触发 tool use 时不再断开连接

## 约束

- 仅修改 `TransferFromCompletionsUtils.convertStreamResponse()` 和 `StreamMessagesCallback.callback()` 中与 tool_use index 相关的逻辑
- 不引入新的类或文件（bug fix 范围内解决）
- 保持对 thinking、text 阶段已有 index 管理逻辑的兼容
- Java 8 兼容

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `api/sdk/src/main/java/com/ke/bella/openapi/protocol/message/TransferFromCompletionsUtils.java` | 修改 | `convertStreamResponse()` 方法移除内部 `contentIndex + 1` 逻辑，tool call start 事件使用传入的 contentIndex |
| `api/server/src/main/java/com/ke/bella/openapi/protocol/message/StreamMessagesCallback.java` | 修改 | `callback()` 方法重构 tool_use 阶段的 index 管理，统一由 callback 层控制 index 递增时机 |

## 实现思路

### Step 1: 修复 `TransferFromCompletionsUtils.convertStreamResponse()` 中 tool call 的 index 逻辑

**当前问题**：第 528 行 `contentIndex = contentIndex + 1` 在检测到新 tool call name 时自增了 index，导致该方法返回的 `content_block_start` 使用了 `contentIndex + 1`，而调用方 `StreamMessagesCallback` 随后也会自增，造成 double-increment。

**修改方案**：
1. 移除第 528 行的 `contentIndex = contentIndex + 1`
2. `content_block_start` 和 `content_block_delta` 统一使用传入的 `contentIndex` 参数
3. 方法签名不变，返回值语义不变

**验证方式**：单元测试构造含 tool_calls 的 `StreamCompletionResponse`，断言返回的 `content_block_start` 和 `content_block_delta` 的 index 一致且等于传入的 contentIndex。

### Step 2: 修复 `StreamMessagesCallback.callback()` 中 tool_use 阶段切换的 index 管理

**当前问题**：当 `stage == 3 && currentStage == 3`（连续 tool call）时，第 89 行 `curChoiceIndex += 1` 用来标记新 block，但 `contentIndex` 的递增时机与 `convertStreamResponse` 内部的自增冲突。此外，从非 tool 阶段进入 tool 阶段时（`currentStage != stage` 且 `currentStage == 3`），`contentIndex` 的递增由 `convertStreamResponse` 内部完成，callback 层未感知。

**修改方案**：
1. 在进入 `stage == 3`（tool call 阶段）时，由 callback 层在检测到 `messages` 中包含 `content_block_start` 时主动递增 `contentIndex`
2. 对于连续 tool call（`stage == 3 && currentStage == 3`），在插入上一个 block 的 `content_block_stop` 后递增 `contentIndex`，然后更新 `messages` 中相关事件的 index
3. 确保 `content_block_delta` 类型的事件（input_json_delta）使用当前 `contentIndex` 而非递增后的值

**验证方式**：构造 mock SSE 输出场景（text + tool_use、纯 tool_use、多 tool_use），逐事件检查 index 连续性和一致性。

### Step 3: 防御空 delta / finish-only chunk

**当前问题**：当 OpenAI 流式响应发送只含 `finish_reason` 而 delta 为空的 chunk 时，`getCurrentStage()` 返回 `0`，可能导致非预期的 stage 切换判断。

**修改方案**：
1. 在 `StreamMessagesCallback.callback()` 中，当 `getCurrentStage()` 返回 0 时跳过 stage 切换逻辑，保持当前 stage 不变
2. 确保 `convertStreamResponse()` 对空 delta 不生成任何 content block 事件（当前已有 null/empty 检查，确认覆盖即可）

**验证方式**：构造只含 finish_reason 的 chunk，断言不生成额外的 `content_block_start`。

### Step 4: 编写/补充单元测试

**文件**：`api/server/src/test/java/com/ke/bella/openapi/protocol/message/StreamMessagesCallbackTest.java`（如已存在则补充，否则新建）

**测试用例**：
1. `testSingleToolUseIndexConsistency`：单个 tool use block，验证 start/delta/stop index 一致
2. `testMultipleToolUseIndexIncrement`：连续多个 tool use block，验证 index 严格递增
3. `testTextToToolUseTransition`：text → tool_use 切换，验证 stop/start 顺序和 index
4. `testThinkingToToolUseTransition`：thinking → tool_use 切换场景
5. `testFinishOnlyChunkNoExtraBlock`：只含 finish_reason 的 chunk 不生成多余 block
6. `testConvertStreamResponseToolCallIndex`：直接测试 `TransferFromCompletionsUtils.convertStreamResponse()` 的 index 输出

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 修改 `convertStreamResponse` 方法语义可能影响其他调用方 | 中 | 全局搜索确认仅 `StreamMessagesCallback` 调用该方法；review 时重点检查 |
| thinking → text → tool_use 多阶段切换的边界场景 | 中 | 补充覆盖 thinking/text/tool 三阶段切换的测试用例 |
| 连续多 tool call 场景（如 parallel tool use）index 管理复杂 | 中 | 参考 Anthropic Messages 协议规范，编写多 tool 场景端到端测试 |
| 修改后 curChoiceIndex 语义变化导致其他逻辑失效 | 低 | curChoiceIndex 仅用于 stage 切换判断，修改后进行完整回归 |

**外部依赖**：
- 验证需要可用的 Deepseek-V4-Pro 渠道配置
- 完整端到端测试需要 ClaudeCode 客户端环境

## 验证方式

1. **单元测试**：运行 `mvn test -pl server -Dtest=StreamMessagesCallbackTest`（需连接数据库环境）
2. **手动验证**：使用 curl 发送 `/v1/messages` stream 请求（带 tools 参数），抓取 SSE 事件流，逐行检查 tool_use block 的 index
3. **端到端验证**：ClaudeCode 通过 gateway 的 `/v1/messages` stream 请求 Deepseek-V4-Pro，触发 tool use，确认不再断开连接
4. **回归检查**：验证 thinking、text 阶段的 index 管理未受影响（纯 text 响应、thinking + text 响应场景）

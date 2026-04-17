# ChatStore 单元测试说明

## 测试覆盖

本测试文件对 `chat/store.ts` 进行了全面的单元测试，覆盖以下关键功能：

### 1. appendToken 性能测试 ⚡

测试 `appendTextToken` 函数在高频调用场景下的性能表现：

- **10000 次单字符 token 追加**
  - 验证：typingBuffer、segmentBuffer、segments 的正确累积
  - 性能指标：< 100ms（实际约 2ms）
  - 验证 buffer flush 机制（每 2KB 触发一次）

- **10000 次多字符 token 追加**
  - 使用 3 字符 token ("abc")
  - 总字符数：30000
  - 验证内容完整性和 buffer 机制

### 2. segmentBuffer flush 机制 🔄

测试三层缓冲架构的核心逻辑：

- **达到 2KB 阈值时自动 flush**
  - 追加 2048 字符后触发 flush
  - segments 数组新增元素
  - segmentBuffer 清空

- **超过 2KB 时立即 flush**
  - 小 token (1000) + 大 token (1500) = 2500 > 2048
  - 验证 flush 时机正确

- **多次小 token 累积后 flush**
  - 25 次 × 100 字符 = 2500 字符
  - 验证累积 flush 机制

- **typingBuffer 持续累积不受影响**
  - typingBuffer 用于 UI 打字机效果
  - 不会因 flush 而清空
  - 与 segments + segmentBuffer 内容一致

### 3. finishStreaming 流程 ✅

测试流式输出结束时的状态清理：

- **flush segmentBuffer 到 segments**
  - 未达阈值的 buffer 内容被持久化

- **清空 typingBuffer**
  - UI 打字机效果缓冲区清空

- **清空 streamingMessageId 和 streamingBlockId**
  - 流式状态重置

- **处理空 segmentBuffer**
  - 已 flush 的情况下不添加空 segment

- **处理异常状态**
  - 无 streamingMessageId 时安全返回

### 4. message 状态变化 🔄

测试消息生命周期中的状态转换：

- **创建时 status = 'streaming'**
- **完成后 status = 'done'**
- **完整状态转换流程**
  - streaming → streaming (追加内容) → done (完成)
- **多消息独立管理**
  - 第一个消息：done
  - 第二个消息：streaming

### 5. 边界情况处理 ⚠️

测试异常和边界场景：

- **无 streamingBlockId 时调用 appendTextToken**
  - console.warn 警告
  - 安全返回不抛错

- **无 streamingMessageId 时调用 appendTextToken**
  - console.warn 警告
  - 安全返回不抛错

- **image block 调用 appendTextToken**
  - 被忽略（image block 无 text 属性）

- **空 token 追加**
  - length = 0
  - typingBuffer = ''

- **连续多次 finishStreaming**
  - 第二次调用不报错

### 6. 综合场景测试 🎭

测试真实的多 block 流式场景：

- 创建 assistant 消息
- 添加 text block：'First block'
- 添加 code block (typescript)：'const x = 1;'
- 添加 reasoning block：'Think'
- 完成流式
- 验证：
  - 3 个 blocks 正确创建
  - 最后一个 block 被 flush
  - 前面的 blocks 保留在 segmentBuffer
  - 消息状态 = 'done'

## 运行测试

```bash
# 运行单元测试
npm test -- store.test.ts

# 运行测试并查看覆盖率
npm run test:coverage -- store.test.ts

# 监听模式（开发时使用）
npm run test:watch -- store.test.ts
```

## 测试结果

✅ **21/21 测试通过**

### 性能指标

- 10000 次 appendToken：~2ms ✨
- 单次测试套件：~350ms
- 总计：21 个测试用例全部通过

## 测试架构

### Mock 策略

- **nanoid**：Mock 为固定 ID 生成器 `mock-id-{counter}`
- 便于断言和调试

### Store 重置

每个测试前自动重置：
```typescript
beforeEach(() => {
  mockIdCounter = 0
  useChatStore.setState({
    messageIds: [],
    messageMap: {},
    streamingMessageId: undefined,
    streamingBlockId: undefined,
  })
})
```

### 状态读取模式

```typescript
// ✅ 正确：调用后重新获取状态
useChatStore.getState().startAssistantMessage()
const state = useChatStore.getState()
const messageId = state.streamingMessageId

// ❌ 错误：状态可能过期
const store = useChatStore.getState()
store.startAssistantMessage()
console.log(store.streamingMessageId) // 可能是旧值
```

## 关键发现

### 三层缓冲机制

1. **typingBuffer** 🥉
   - 用途：UI 打字机效果
   - 行为：持续累积，不受 flush 影响
   - 清空时机：finishStreaming

2. **segmentBuffer** 🥈
   - 用途：聚合 token，减少写入频率
   - 行为：达到 2KB 自动 flush
   - 清空时机：flush 或 finishStreaming

3. **segments** 🥇
   - 用途：持久化存储
   - 行为：低频写入（每 2KB 一次）
   - 内容：已固化的文本段数组

### finishStreaming 行为

只处理当前流式的 block（最后一个），前面的 blocks 内容保留在原有状态：
- 最后一个 block：flush + 清空 typingBuffer
- 之前的 blocks：保持原状

这是合理的设计，因为流式场景下只有最后一个 block 是"正在输入"的。

## 注意事项

⚠️ **Zustand 状态更新**
- 调用 action 后必须重新 `getState()` 获取最新状态
- 不能缓存 store 对象

⚠️ **buffer flush 逻辑**
- 一次性追加 > 2KB 的内容会全部 flush
- 累积追加达到阈值才触发 flush

⚠️ **多 block 场景**
- 每次 `startBlock` 会切换 `streamingBlockId`
- 前一个 block 的内容保留（不会自动 flush）

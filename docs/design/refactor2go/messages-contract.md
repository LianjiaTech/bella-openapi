# Task 1 / Messages 契约盘点

## 1. 范围说明

本文从总表中拆出 `Messages` 能力点，聚焦 `/v1/messages` 在 Go 重构前必须保持兼容的对外协议、流式语义、治理约定与运行不变量。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/messages` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Content-Type

- 请求默认是 `application/json`
- 流式响应默认是 `text/event-stream`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `anthropic_version`
  - `model`
  - `messages`
  - `system`
  - `max_tokens`
  - `metadata`
  - `stop_sequences`
  - `stream`
  - `temperature`
  - `thinking`
  - `output_config`
  - `tool_choice`
  - `tools`
  - `top_k`
  - `top_p`
- `system` 既可能是字符串，也可能是文本 block 数组
- `system` 为 block 数组时，当前至少继续兼容：
  - `type`
  - `text`
  - `cache_control.type`
- `messages` 是输入消息数组
- `messages[]` 元素核心是 `role` 与 `content`
- `content` 可以是字符串，也可以是 block 数组
- `content[]` 当前至少继续兼容这些 block：
  - `{ "type": "text", "text": "..." }`
  - `{ "type": "image", "source": { "type": "url|base64", ... } }`
  - `{ "type": "document", "source": { "type": "url|base64|file|content|text", ... } }`
  - `{ "type": "tool_use", "id": "...", "name": "...", "input": { ... } }`
  - `{ "type": "tool_result", "tool_use_id": "...", "content": "...", "is_error": false }`
  - `{ "type": "thinking", "thinking": "...", "signature": "..." }`
  - `{ "type": "redacted_thinking", "data": "..." }`
- `content[]` 公共字段当前至少继续兼容：
  - `type`
  - `cache_control.type`
- `image.source` 当前至少继续兼容：
  - `type`
  - `url`
  - `media_type`
  - `data`
- `document.source` 当前至少继续兼容：
  - `type`
  - `url`
  - `media_type`
  - `data`
  - `file_id`
  - `content`
- `tool_use` 当前至少继续兼容：
  - `id`
  - `name`
  - `input`
- `tool_result` 当前至少继续兼容：
  - `tool_use_id`
  - `content`
  - `is_error`
- `tools[]` 当前继续兼容 message tool 定义：
  - `name`
  - `description`
  - `input_schema`
  - `cache_control`
- `tools[].input_schema` 当前至少继续兼容：
  - `type`
  - `properties`
  - `required`
  - `additionalProperties`
- `tool_choice` 当前继续兼容结构化对象表达，而不是仅限字符串枚举
- `tool_choice` 当前至少继续兼容：
  - `type=auto|any|tool|none`
  - 当 `type=tool` 时继续兼容 `name`
- `thinking` 当前继续兼容：
  - `type`
  - `budget_tokens`
- `output_config` 当前继续兼容：
  - `effort`
  - `format`
- `output_config.format` 当前至少继续兼容：
  - `type`
  - `schema`
- `metadata` 当前至少继续兼容：
  - `user_id`
- 未知请求字段当前不应被入口静默丢弃

#### 2.1.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "anthropic_version": "bedrock-2023-05-31",
    "model": "claude-3-7-sonnet",
    "messages": [
      { "role": "user", "content": "hello" }
    ],
    "max_tokens": 1024,
    "stream": false
  }
  ```
- 多模态 / 工具调用请求仍应兼容：
  ```json
  {
    "model": "claude-3-7-sonnet",
    "system": "You are helpful.",
    "messages": [
      {
        "role": "user",
        "content": [
          { "type": "text", "text": "帮我看下图片" },
          { "type": "image", "source": { "type": "url", "url": "https://..." } }
        ]
      }
    ],
    "tools": [
      {
        "name": "search_docs",
        "description": "...",
        "input_schema": { "type": "object", "properties": {} }
      }
    ],
    "tool_choice": { "type": "auto" }
  }
  ```

#### 2.1.5 特殊 Header

- 共享链路仍会受 `X-BELLA-MOCK-REQUEST` 等公共 header 影响

### 2.2 `POST /v1/messages` 输出协议

#### 2.2.1 Content-Type

- 非流式响应默认是 `application/json`
- 流式响应默认是 `text/event-stream`

#### 2.2.2 成功响应

##### 非流式响应

- 顶层仍是 message JSON object，例如：
  ```json
  {
    "id": "msg_123",
    "type": "message",
    "role": "assistant",
    "content": [
      { "type": "text", "text": "hello" }
    ],
    "model": "claude-3-7-sonnet",
    "stop_reason": "end_turn",
    "stop_sequence": null,
    "usage": {
      "input_tokens": 10,
      "output_tokens": 5
    }
  }
  ```
- 关键字段：
  - `id`
  - `type=message`
  - `role=assistant`
  - `content`
  - `model`
  - `stop_reason`
  - `stop_sequence`
  - `usage`
- `content[]` 当前至少继续兼容：
  - `text`
  - `tool_use`
  - `server_tool_use`
  - `web_search_tool_result`
  - `code_execution_tool_result`
  - `mcp_tool_use`
  - `mcp_tool_result`
  - `container_upload`
  - `thinking`
  - `redacted_thinking`
- `content[]` 各 block 当前至少继续兼容以下属性层次：
  - `text.text`
  - `tool_use.id`
  - `tool_use.name`
  - `tool_use.input`
  - `server_tool_use.tool_use_id`
  - `server_tool_use.name`
  - `server_tool_use.input`
  - `web_search_tool_result.tool_use_id`
  - `web_search_tool_result.documents`
  - `web_search_tool_result.is_error`
  - `code_execution_tool_result.tool_use_id`
  - `code_execution_tool_result.stdout`
  - `code_execution_tool_result.stderr`
  - `code_execution_tool_result.is_error`
  - `mcp_tool_use.tool_use_id`
  - `mcp_tool_use.name`
  - `mcp_tool_use.input`
  - `mcp_tool_result.tool_use_id`
  - `mcp_tool_result.output`
  - `mcp_tool_result.is_error`
  - `container_upload.tool_use_id`
  - `container_upload.container_name`
  - `container_upload.file_name`
  - `container_upload.full_path`
  - `container_upload.object_name`
  - `container_upload.status`
  - `container_upload.message`
  - `thinking.thinking`
  - `thinking.signature`
  - `redacted_thinking.data`
- `usage` 当前至少继续兼容：
  - `input_tokens`
  - `output_tokens`
  - `cache_creation_input_tokens`
  - `cache_read_input_tokens`
  - `server_tool_use`
  - `service_tier`
- `usage.cache_creation` 当前至少继续兼容：
  - `ephemeral_1h_input_tokens`
  - `ephemeral_5m_input_tokens`
- `usage.server_tool_use` 当前至少继续兼容：
  - `web_search_requests`
- 顶层还继续可能出现：
  - `thought_signature`

##### 流式响应

- 以命名 SSE 事件返回，例如：
  ```text
  event: message_start
  data: {"type":"message_start","message":{"id":"msg_123","type":"message","role":"assistant","content":[],"model":"claude-3-7-sonnet","usage":{"input_tokens":10,"output_tokens":1}}}

  event: content_block_delta
  data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hel"}}

  event: message_delta
  data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"input_tokens":10,"output_tokens":5}}

  event: message_stop
  data: {"type":"message_stop"}
  ```
- 关键事件类型包括：
  - `message_start`
  - `content_block_start`
  - `content_block_delta`
  - `content_block_stop`
  - `message_delta`
  - `message_stop`
- `content_block_delta` 可继续细分为：
  - `text_delta`
  - `input_json_delta`
  - `thinking_delta`
  - `signature_delta`
  - `redacted_thinking`
- 各 delta 当前至少继续兼容：
  - `text_delta.text`
  - `input_json_delta.partial_json`
  - `thinking_delta.thinking`
  - `signature_delta.signature`
  - `redacted_thinking.data`
- `message_delta.delta` 当前继续承载 `stop_reason` 与 `stop_sequence`
- `message_delta.usage` 当前继续承载最终 token 统计
- 正常结束以 `message_stop` 作为终止事件

#### 2.2.3 错误响应

##### 同步错误

- 返回统一错误体，而不是 `type=message` 的成功响应
- 最小错误骨架仍应兼容：
  ```json
  {
    "error": {
      "code": "400",
      "httpCode": 400,
      "message": "Unsupported protocol.",
      "type": "Illegal Argument"
    }
  }
  ```

##### 流式错误

- 当前至少继续兼容 message 协议风格的命名错误事件，例如：
  ```text
  event: error
  data: {"type":"error","error":{"type":"api_error","message":"..."}}
  ```
- 流式异常后不应退化成 chat completion 的 `[DONE]` 结束方式
- 流式异常后也不应改成普通 JSON success body

## 3. 不能改变的行为

下列各项既是现状行为说明，也是 Go 重构时的验收要点。

- **协议转换规则**
  - messages 对外继续表现为 message 协议，而不是 chat completion 协议
- **流式分段规则**
  - `stream=true` 时继续返回 message 协议的命名 SSE 事件，而不是 chat completion 的未命名 `data:` 事件
  - thinking / text / tool_use 等不同内容类型，继续以各自对应的 content block / delta 事件序列对外呈现，不能混成单一文本流
  - 正常结束继续以 `message_stop` 作为终止事件，而不是使用 `[DONE]`
- **路由规则**
  - messages 继续先选出 channel，再依据选中的协议决定后续转换与调用方式
- **错误与收口规则**
  - 同步错误继续表现为统一错误体
  - 流式错误继续保持 message 协议风格的命名事件 / 断流语义，不能退化成 completions 的 `[DONE]`
  - 一旦已经开始发送 message 协议事件，后续失败继续通过协议内错误事件表达，不能退回普通 HTTP JSON 错误
- **usage 补齐规则**
  - `message_delta.usage` 继续被视为最终 token 统计的主要承载位置
  - 当上游 `message_delta.usage` 只给出 `output_tokens`、`input_tokens=0` 时，当前继续用 `message_start` 中已拿到的输入 token 与 cache 信息补齐
  - 如果上游后续事件已经给出非零 `input_tokens`，继续优先沿用上游值，而不是被旧值覆盖
- **收口与治理规则**
  - 流式首包时间继续以第一个真正发送给客户端的 message 应用事件为准，而不是以 HTTP 连接建立时间为准
  - 正常完成继续以 `message_stop` 结束，并由服务端主动结束 SSE
  - 超时、客户端中断、下游中断等异常场景，继续遵循“先记录当前 processData / requestId，再结束流”的收口原则
  - 流式与非流式都存在统一日志收口
  - 非 private 请求会触发治理侧副作用，如并发计数

## 4. 回归验证基线

回归验证不以“内部实现是否一致”为标准，而以“外部协议、可观察行为、治理口径是否保持一致”为标准。至少应覆盖以下基线。

- **输入协议基线**
  - 能正常接收最小 messages 请求骨架
  - 能正常接收包含 `system`、多模态 `content`、`tools`、`tool_choice`、`thinking` 的请求
  - `content` 为字符串与 block 数组两种形式时，入口行为保持一致
  - 公共扩展 header 仍能进入共享链路
- **非流式输出基线**
  - 成功响应继续保持 `type=message` 的对象形态
  - `content[]`、`stop_reason`、`stop_sequence`、`usage` 的结构与语义保持兼容
  - tool / thinking / mcp 等不同 block 类型仍能按现状出现在响应中
- **流式输出基线**
  - `stream=true` 时继续返回命名 SSE 事件，而不是未命名 `data:` chunk
  - `message_start`、`content_block_*`、`message_delta`、`message_stop` 的序列语义保持兼容
  - thinking、text、tool_use 等内容类型仍以各自事件序列对外表达
  - 正常结束仍以 `message_stop` 表示
- **错误协议基线**
  - 同步错误继续返回统一 `error` object
  - 流式错误继续以 `event: error` 风格的命名事件表达
  - 流式错误后不出现 `[DONE]` 或普通 success body
- **行为不变量基线**
  - message 协议与 chat completion 协议的边界保持不变
  - usage 补齐位置与补齐优先级保持一致
  - 流式首包、异常收口、统一日志收口、非 private 治理副作用保持一致

## 5. 验证方式

- **契约样例回放**
  - 基于 Java 基线环境沉淀最小请求、多模态请求、工具调用请求、thinking 请求、流式请求等样例
  - 分别对 Java 与 Go 发起请求，按关键字段、字段类型、事件序列与终止事件进行比对
- **黄金样本比对**
  - 为非流式成功、流式成功、同步错误、流式错误建立黄金样本
  - 允许在 ID、时间戳、模型自然波动文本上存在可控差异，但不允许协议形态漂移
- **流式序列验证**
  - 重点验证 `message_start -> content_block_* -> message_delta -> message_stop` 的顺序与字段位置
  - 验证 `message_delta.usage`、thinking delta、tool block 事件在流中的承载位置不变
- **异常路径验证**
  - 验证限流、超时、上游失败、客户端中断等场景
  - 验证这些场景下仍通过 message 协议内错误事件或统一错误体收口
- **治理与观测验证**
  - 通过日志、requestId / processData、usage 记录、并发计数等观察收口结果
  - 重点验证非 private 请求是否仍命中治理副作用

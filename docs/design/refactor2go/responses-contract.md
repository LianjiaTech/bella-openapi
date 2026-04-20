# Task 1 / Responses 契约盘点

## 1. 范围说明

本文从总表中拆出 `Responses` 能力点，聚焦 `/v1/responses` 创建与查询能力在 Go 重构前必须保持兼容的对外协议、流式语义、治理约定与运行不变量。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/responses` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Content-Type

- 创建请求默认是 `application/json`
- 流式响应默认是 `text/event-stream`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `model`
  - `input`
  - `instructions`
  - `temperature`
  - `max_tokens`
  - `top_p`
  - `frequency_penalty`
  - `presence_penalty`
  - `stream`
  - `store`
  - `background`
  - `previous_response_id`
  - `tools`
  - `tool_choice`
  - `include`
  - `metadata`
  - `reasoning`
  - `prompt_cache_key`
- `input` 既可能是字符串，也可能是 input item 数组
- `input[]` 当前至少继续兼容：
  - `{ "type": "message", "role": "user|assistant|system", "content": "..." }`
  - `{ "type": "function_call", "call_id": "...", "name": "...", "arguments": "..." }`
  - `{ "type": "function_call_output", "call_id": "...", "output": "..." }`
- `input[]` 元素字段层次当前至少继续兼容：
  - `type`
  - `role`
  - `content`
  - `call_id`
  - `name`
  - `arguments`
  - `output`
  - `status`
- `message.content` 为数组时当前至少继续兼容：
  - `type`
  - `text`
  - `image_url`
  - `file_id`
  - `audio_url`
  - `detail`
- `tools[]` 当前至少继续兼容：
  - `type`
  - `name`
  - `description`
  - `parameters`
  - `strict`
- `reasoning` 当前继续兼容结构化对象：
  - `effort`
  - `summary`
  - 以及 `_extra_body` 中吸收的扩展字段
- `metadata` 当前继续兼容 string->object 的 map 结构
- `include` 当前继续兼容字符串数组
- 未知请求字段当前继续被吸收到 `_extra_body`，而不是在入口直接拒绝

#### 2.1.4 请求示例与语义

- 最小创建请求骨架仍应保持为：
  ```json
  {
    "model": "gpt-4.1",
    "input": "hello",
    "stream": false
  }
  ```
- 带 input item / tool 的请求仍应兼容：
  ```json
  {
    "model": "gpt-4.1",
    "input": [
      {
        "type": "message",
        "role": "user",
        "content": [
          { "type": "input_text", "text": "帮我查天气" }
        ]
      }
    ],
    "tools": [
      {
        "type": "function",
        "name": "get_weather",
        "description": "...",
        "parameters": { "type": "object", "properties": {} }
      }
    ],
    "tool_choice": "auto"
  }
  ```

#### 2.1.5 特殊 Header

- `X-BELLA-CHANNEL`
- 创建接口可选，若提供则优先按 channel code 直选 channel

### 2.2 `POST /v1/responses` 输出协议

#### 2.2.1 Content-Type

- 非流式响应默认是 `application/json`
- 流式响应默认是 `text/event-stream`

#### 2.2.2 成功响应

##### 非流式响应

- 顶层仍是 response JSON object，例如：
  ```json
  {
    "id": "resp_123",
    "object": "response",
    "created": 1710000000,
    "model": "gpt-4.1",
    "status": "completed",
    "output_text": "hello",
    "output": [],
    "usage": {
      "input_tokens": 10,
      "output_tokens": 5,
      "total_tokens": 15
    },
    "_bella_response": {
      "channel_code": "channel_x"
    }
  }
  ```
- 关键字段：
  - `id`
  - `object`
  - `created`
  - `model`
  - `status`
  - `output_text`
  - `output`
  - `usage`
  - `reasoning`
  - `metadata`
  - `store`
  - `background`
  - `_bella_response`
- Bella 侧还会补 `_bella_response.channel_code`
- `output[]` 当前至少继续兼容：
  - `type`
  - `id`
  - `role`
  - `content`
  - `status`
  - `call_id`
  - `name`
  - `arguments`
  - `summary`
  - `encrypted_content`
- `output[]` 元素字段层次当前至少继续兼容：
  - `type`
  - `id`
  - `role`
  - `content[]`
  - `status`
  - `call_id`
  - `name`
  - `arguments`
  - `summary[]`
  - `encrypted_content`
- `output[].content[]` 当前至少继续兼容：
  - `type`
  - `text`
  - `annotations`
- `output[].summary[]` 当前至少继续兼容：
  - `type`
  - `text`
- `usage` 当前至少继续兼容：
  - `input_tokens`
  - `output_tokens`
  - `total_tokens`
  - `input_tokens_details`
  - `output_tokens_details`
  - `tool_usage`
  - `tool_usage_details`
- `usage.input_tokens_details` 当前至少继续兼容：
  - `cached_tokens`
- `usage.output_tokens_details` 当前至少继续兼容：
  - `reasoning_tokens`
- `reasoning` 当前至少继续兼容：
  - `effort`
  - `summary`
- `_bella_response` 当前至少继续兼容：
  - `channel_code`

##### 流式响应

- 以 Responses API 原生命名 SSE 事件返回，例如：
  ```text
  event: response.created
  data: {"type":"response.created","sequence_number":0,"response":{"id":"resp_123","object":"response","model":"gpt-4.1","status":"in_progress"}}

  event: response.output_text.delta
  data: {"type":"response.output_text.delta","sequence_number":1,"output_index":0,"content_index":0,"delta":"Hel"}

  event: response.completed
  data: {"type":"response.completed","sequence_number":2,"response":{"id":"resp_123","status":"completed","usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15}}}
  ```
- 关键事件包括：
  - `response.created`
  - `response.completed`
  - `response.error`
- 上游还可能产生：
  - `response.in_progress`
  - `response.output_item.added`
  - `response.output_text.delta`
  - `response.function_call_arguments.delta`
  - `response.reasoning_summary_text.delta`
- 每个事件 data 当前继续兼容：
  - `type`
  - `sequence_number`
  - `response`
  - `item`
  - `delta`
  - `output_index`
  - `content_index`
  - `summary_index`
- 其中：
  - `response` 当前继续承载 response 顶层对象或其终态子集
  - `item` 当前继续承载 `output_item.added` 等事件中的单个输出项
  - `delta` 当前继续承载文本、函数参数、reasoning summary 等增量内容
- 正常结束继续依赖 `response.completed` 之后的流关闭，而不是发送 `[DONE]`

#### 2.2.3 错误响应

##### 同步错误

- 返回统一错误体，而不是 response success body
- 最小错误骨架仍应兼容：
  ```json
  {
    "error": {
      "code": "400",
      "httpCode": 400,
      "message": "model is required",
      "type": "Illegal Argument"
    }
  }
  ```

##### 流式错误

- 通过命名事件 `response.error` 表达
- 最小错误事件仍应兼容：
  ```text
  event: response.error
  data: {"error":{"code":"502","httpCode":502,"message":"...","type":"Channel Exception"}}
  ```
- 流式错误后应结束连接，且不应退化成 `[DONE]`

### 2.3 `GET /v1/responses/{response_id}` 输入协议

#### 2.3.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.3.2 Path 参数

- 核心参数：
  - `response_id`

#### 2.3.3 特殊 Header

- `X-BELLA-CHANNEL`
- 查询接口当前必填

#### 2.3.4 请求语义

- `GET /v1/responses/{response_id}` 通过 path 中的 `response_id` 查询已存在 response
- 查询结果仍是 response JSON object，而不是 list 或裸字符串
- 当前请求仍要求配合 `X-BELLA-CHANNEL` 才能定位 channel

### 2.4 `GET /v1/responses/{response_id}` 输出协议

#### 2.4.1 Content-Type

- 响应默认是 `application/json`

#### 2.4.2 成功响应

- 查询成功继续返回 response JSON object
- 关键字段：
  - `id`
  - `object`
  - `created`
  - `model`
  - `status`
  - `output_text`
  - `output`
  - `usage`
  - `reasoning`
  - `_bella_response.channel_code`
- 查询响应中的字段层次继续与创建成功响应保持一致，不得缩成 `status + output_text` 的简化版本

#### 2.4.3 错误响应

- `response_id` 缺失、`X-BELLA-CHANNEL` 缺失、channel 不存在或查询失败时继续返回统一错误体

## 3. 不能改变的行为

下列各项既是现状行为说明，也是 Go 重构时的验收要点。

- **模型规则**
  - `model` 继续来自 `ResponsesApiRequest.model`
  - 创建接口缺失 `model` 时继续返回参数错误
- **路由规则**
  - 创建接口默认按 `endpoint + model` 选路
  - 提供 `X-BELLA-CHANNEL` 时继续优先按 channel code 直选 channel
  - 查询接口继续依赖 `X-BELLA-CHANNEL` 定位 channel，而不是按 `response_id` 全局查找
- **查询规则**
  - `GET /v1/responses/{response_id}` 继续作为查询 response 状态或内容的标准入口
  - `response_id` 为空或 `X-BELLA-CHANNEL` 缺失时继续返回参数错误
- **响应增强规则**
  - 创建与查询结果继续补 `_bella_response.channel_code`
  - 创建成功响应继续保持 response 对象的完整顶层字段，而不是仅返回 `response_id`
- **流式与错误规则**
  - responses 的流式返回继续使用命名 SSE 事件，而不是 chat completion 的未命名 `data:` chunk
  - `response.completed` 继续作为 usage / response_id 提取的关键终态事件
  - 如果流在该事件前异常结束，当前可能拿不到 usage，这一现状需要被显式保留或显式调整
  - 同步错误继续表现为统一错误体
  - 流式错误继续表现为命名事件 `response.error`
  - 一旦已经开始发送 responses 协议事件，后续失败继续通过 `response.error` 等流内事件表达，不能退回普通 HTTP JSON 错误
- **收口约定**
  - 流式首包时间继续以第一个 `response.created` 事件为准
  - 正常完成继续以 `response.completed` 为终态事件，并在其后由服务端结束 SSE
  - 超时、客户端中断、下游中断等异常场景，继续遵循“先记录当前 processData / requestId，再结束流”的收口原则
  - `response.completed` 是 usage / response_id 写回日志上下文的关键事件

## 4. 回归验证基线

回归验证不以“内部实现是否一致”为标准，而以“外部协议、可观察行为、治理口径是否保持一致”为标准。至少应覆盖以下基线。

- **输入协议基线**
  - 能正常接收最小 responses 创建请求骨架
  - 能正常接收 `input` 为字符串与 input item 数组两种形式的请求
  - 能正常接收 `tools`、`tool_choice`、`reasoning`、`metadata` 等结构化字段
  - 查询接口仍通过 `response_id + X-BELLA-CHANNEL` 定位结果
- **非流式输出基线**
  - 成功响应继续保持 response object 形态
  - `output_text`、`output[]`、`usage`、`_bella_response.channel_code` 的结构与语义保持兼容
  - 创建与查询接口都继续返回完整 response 对象，而不是简化结果
- **流式输出基线**
  - `stream=true` 时继续返回 Responses API 命名 SSE 事件
  - `response.created`、`response.output_text.delta`、`response.completed` 等关键事件的顺序与字段位置保持兼容
  - 正常结束继续以 `response.completed` 为终态，而不是 `[DONE]`
- **错误协议基线**
  - 同步错误继续返回统一 `error` object
  - 流式错误继续通过 `response.error` 事件表达，并在错误后结束连接
  - 已开始发送 responses 事件后，不退回普通 HTTP JSON 错误
- **行为不变量基线**
  - 创建接口与查询接口的选路规则保持一致
  - `X-BELLA-CHANNEL` 的可选 / 必填语义保持一致
  - `_bella_response.channel_code` 补充规则保持一致
  - usage / response_id 的流式收口提取时机保持一致

## 5. 验证方式

- **契约样例回放**
  - 基于 Java 基线环境沉淀创建成功、创建流式、查询成功、同步错误、流式错误等样例
  - 分别对 Java 与 Go 发起请求，按关键字段、字段类型、事件类型、终态事件进行比对
- **黄金样本比对**
  - 为最小创建请求、带工具请求、查询请求、流式完成、流式错误建立黄金样本
  - 允许在 ID、时间戳、自然语言输出文本上存在可控差异，但不允许协议形态漂移
- **流式终态验证**
  - 重点验证 `response.created` 到 `response.completed` 的事件序列
  - 验证 usage、response_id、状态字段仍在预期事件中出现
- **查询路径验证**
  - 验证缺少 `response_id` 或缺少 `X-BELLA-CHANNEL` 时仍返回参数错误
  - 验证提供 `X-BELLA-CHANNEL` 时优先按 channel code 定位查询链路
- **异常路径与收口验证**
  - 验证上游失败、超时、客户端中断等场景
  - 验证这些场景下仍通过统一错误体或 `response.error` 收口，并保留既有日志上下文写回语义

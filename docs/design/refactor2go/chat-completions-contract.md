# Task 1 / Chat Completions 契约盘点

## 1. 范围说明

本文从总表中拆出 `Chat Completions` 能力点，聚焦 `/v1/chat/completions` 在 Go 重构前必须保持兼容的对外协议、流式语义、治理约定与运行不变量。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/chat/completions` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Content-Type

- 请求默认是 `application/json`
- 流式响应默认是 `text/event-stream`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `model`
  - `user`
  - `messages`
  - `functions`
  - `tools`
  - `function_call`
  - `tool_choice`
  - `temperature`
  - `top_p`
  - `n`
  - `stream`
  - `stream_options`
  - `stop`
  - `max_tokens`
  - `presence_penalty`
  - `frequency_penalty`
  - `logprobs`
  - `top_logprobs`
  - `logit_bias`
  - `response_format`
  - `seed`
  - `parallel_tool_calls`
  - `reasoning_effort`
  - `enable_thinking`
  - `thinking`
  - `prompt_cache_key`
- `messages` 是消息数组
- `messages[]` 元素核心结构：
  - `role`：至少兼容 `system`、`user`、`assistant`、`tool`、`function`
  - `content`：既可能是字符串，也可能是内容块数组
  - `name`：`role=function` 时继续兼容
  - assistant / tool 相关消息继续允许携带 `tool_calls`、`tool_call_id`、`function_call`
- 当 `content` 是数组时，当前继续兼容多模态内容块，例如：
  - `{ "type": "text", "text": "..." }`
  - `{ "type": "image_url", "image_url": { "url": "..." } }`
- `messages[].content[]` 当前至少继续兼容：
  - `type=text` 时的 `text`
  - `type=image_url` 时的 `image_url.url`
- `tools[]` 当前继续以 OpenAI function tool 结构为主：
  - `type`
  - `function`
  - `cache_control`
- `tools[].function` 当前至少继续兼容：
  - `name`
  - `description`
  - `parameters.type`
  - `parameters.required`
  - `parameters.properties`
  - `parameters.additionalProperties`
- `functions[]` 与 `function_call` 当前仍继续兼容，但已是旧字段语义
- `functions[]` 当前至少继续兼容：
  - `name`
  - `description`
  - `parameters.type`
  - `parameters.required`
  - `parameters.properties`
  - `parameters.additionalProperties`
- `function_call` 当前继续兼容：
  - 字符串枚举：`none`、`auto`
  - 对象结构：`name`
- `tool_choice` 当前继续兼容字符串或结构化对象两种表达
- `tool_choice` 为对象时当前至少继续兼容：
  - `type`
  - `function.name`
- `stream_options` 当前至少继续兼容：
  - `include_usage`
- `response_format` 当前继续兼容文本模式与 JSON object 模式
- `response_format` 为对象时当前至少继续兼容：
  - `type`
- `logit_bias` 当前继续兼容 `tokenId -> bias` 的 map 结构
- `thinking` 当前继续兼容结构化对象
- 未在固定字段中的额外请求字段，当前继续允许作为顶层扩展字段透传，而不是在入口直接拒绝
- 平铺扩展字段与 `extra_body` 当前继续都可能被送往下游

#### 2.1.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "gpt-4o",
    "messages": [
      { "role": "user", "content": "hello" }
    ],
    "stream": false
  }
  ```
- 带工具定义时，请求骨架仍应兼容：
  ```json
  {
    "tools": [
      {
        "type": "function",
        "function": {
          "name": "get_weather",
          "description": "...",
          "parameters": {
            "type": "object",
            "properties": {},
            "required": []
          }
        }
      }
    ],
    "tool_choice": "auto"
  }
  ```
- 多轮工具调用消息仍应兼容以下结构：
  ```json
  { "role": "assistant", "tool_calls": [ { "id": "call_x", "type": "function", "function": { "name": "fn", "arguments": "{\"a\":1}" } } ] }
  { "role": "tool", "tool_call_id": "call_x", "content": "..." }
  ```
- `stream=true` 仍表示客户端期望拿到 SSE chunk 流，而不是普通 JSON 一次性响应

#### 2.1.5 特殊 Header

- `X-BELLA-DIRECT=true`
- `X-BELLA-MODEL`
- `X-BELLA-MOCK-REQUEST=true`
- `X-BELLA-FUNCTION-SIMULATE=true`
- `X-BELLA-MERGE-REASONING=true`
- `X-BELLA-SPLIT-REASONING=true`

### 2.2 `POST /v1/chat/completions` 输出协议

#### 2.2.1 Content-Type

- 非流式响应默认是 `application/json`
- 流式响应默认是 `text/event-stream`

#### 2.2.2 成功响应

##### 非流式响应

- 顶层仍是 chat completion response JSON object，例如：
  ```json
  {
    "id": "chatcmpl-xxx",
    "object": "chat.completion",
    "created": 1710000000,
    "model": "gpt-4o",
    "choices": [
      {
        "index": 0,
        "message": { "role": "assistant", "content": "hello" },
        "finish_reason": "stop"
      }
    ],
    "usage": {
      "prompt_tokens": 10,
      "completion_tokens": 5,
      "total_tokens": 15
    },
    "requestRiskData": { "...": "..." },
    "sensitives": { "...": "..." }
  }
  ```
- 关键字段：
  - `id`
  - `object`
  - `created`
  - `model`
  - `choices`
  - `usage`
  - `system_fingerprint`
- 顶层还应继续兼容安全检查附加字段：
  - `requestRiskData`
  - `sensitives`
- `choices[]` 以 `index`、`message`、`finish_reason` 为核心
- `choices[].message` 核心结构：
  - `role`
  - `content`
  - `name`
  - `reasoning_content`
  - `reasoning_content_signature`
  - `redacted_reasoning_content`
  - `tool_calls`
  - `function_call`
- `choices[].message.tool_calls[]` 当前至少继续兼容：
  - `index`
  - `id`
  - `type`
  - `function.name`
  - `function.arguments`
  - `cache_control`
- `choices[].message.function_call` 当前至少继续兼容：
  - `name`
  - `arguments`
- `choices[].finish_reason` 当前继续兼容至少这些语义值：
  - `stop`
  - `length`
  - `function_call`
- `choices[].logprobs` 当前至少继续兼容：
  - `content[]`
  - `refusal[]`
- `choices[].logprobs.content[]` / `refusal[]` 当前至少继续兼容：
  - `token`
  - `logprob`
  - `bytes`
  - `top_logprobs[]`
- `choices[].logprobs.top_logprobs[]` 当前至少继续兼容：
  - `token`
  - `logprob`
  - `bytes`
- `usage` 当前至少继续兼容：
  - `prompt_tokens`
  - `completion_tokens`
  - `total_tokens`
  - `cache_creation_tokens`
  - `cache_read_tokens`
  - `completion_tokens_details.reasoning_tokens`
  - `completion_tokens_details.cached_tokens`
  - `completion_tokens_details.cache_creation_tokens`
  - `completion_tokens_details.audio_tokens`
  - `completion_tokens_details.image_tokens`
  - `prompt_tokens_details.reasoning_tokens`
  - `prompt_tokens_details.cached_tokens`
  - `prompt_tokens_details.cache_creation_tokens`
  - `prompt_tokens_details.audio_tokens`
  - `prompt_tokens_details.image_tokens`

##### 流式响应

- 以 SSE `data:` 事件返回 chat completion chunk 结构，例如：
  ```text
  data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1710000000,"model":"gpt-4o","requestRiskData":{"...":"..."},"choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"}}]}
  data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1710000001,"model":"gpt-4o","choices":[{"index":0,"delta":{"content":"lo"}}]}
  data: {"created":1710000002,"sensitives":{"...":"..."}}
  data: [DONE]
  ```
- chunk 顶层关键字段：
  - `id`
  - `object=chat.completion.chunk`
  - `created`
  - `model`
  - `choices`
- 流式 chunk 顶层同样继续允许附带：
  - `requestRiskData`
  - `sensitives`
- `choices[].delta` 当前继续兼容增量输出：
  - `role`
  - `content`
  - `name`
  - `reasoning_content`
  - `reasoning_content_signature`
  - `redacted_reasoning_content`
  - `tool_calls`
  - `function_call`
- `choices[].finish_reason` 在结束阶段出现
- `tool_calls` 增量当前继续允许同时携带：
  - `id`
  - `type`
  - `function.name`
  - `function.arguments`
- `function_call` 增量当前继续兼容：
  - `name`
  - `arguments`
- 当 `stream_options.include_usage=true` 时，当前继续允许在结束前追加 usage chunk；该 chunk 可出现 `usage` 且 `choices` 为空
- 使用未命名 SSE `data:` 事件
- 正常结束信号是字符串 `[DONE]`

#### 2.2.3 错误响应

##### 同步错误

- 返回统一错误体，而不是 chat completion success body
- 最小错误骨架仍应兼容：
  ```json
  {
    "error": {
      "code": "429",
      "httpCode": 429,
      "message": "rate limit exceeded",
      "type": "Too Many Requests"
    }
  }
  ```
- `error` 对象当前至少继续兼容：
  - `code`
  - `httpCode`
  - `message`
  - `type`
  - `param`
  - `sensitive`
- HTTP 状态码语义仍应与 `error.httpCode` 保持一致

##### 流式错误

- 已建立 SSE 后，错误仍以 SSE `data:` 事件发送，而不是切换成普通 JSON 响应
- 最小错误 chunk 仍应兼容：
  ```text
  data: {"error":{"code":"429","httpCode":429,"message":"rate limit exceeded","type":"Too Many Requests"}}
  ```
- 发送错误 chunk 后应直接结束连接
- 流式错误后当前不应再补发正常 completion chunk，也不应再补 `[DONE]`
- 若流式过程中附带安全检查数据，请保持其仍以同一 SSE data 事件通道发送，而不是改成另一套错误包裹格式

## 3. 不能改变的行为

下列各项既是现状行为说明，也是 Go 重构时的验收要点。

- **模型选择行为**
  - 普通模式下使用请求体中的 `model`
  - direct mode 下改为使用 `X-BELLA-MODEL`
- **支持多模型 fallback 行为**
  - `model` 支持逗号分隔列表
  - 多模型按顺序依次尝试，直到某个模型成功
  - 超过 `bella.openapi.max-models-per-request` 时只尝试前 N 个
  - 全部失败时仅暴露最后一次失败
- **Direct Mode 行为**
  - `X-BELLA-DIRECT=true` 启用 direct mode
  - direct mode 仍会选出 channel，但会跳过 availability check、限流与 safety check
  - 响应是透明透传语义，而不是普通 completion 包装语义
- **Queue / 模拟增强行为**
  - 当 channel 配置了 queue 代理语义时，请求会进入 queue 模式，但对外仍表现为 chat completion 协议
  - 当 function call simulate 开启时，请求或响应可能被改写，但对外仍表现为 completion 协议的一部分
  - reasoning merge / split、tool/function simulate、风险结果插入等能力会影响输出内容与输出结构
- **usage 规则**
  - 非流式响应若上游已返回 `usage`，当前继续直接沿用上游值
  - 若响应缺少 `usage`，当前继续补齐至少 `prompt_tokens`、`completion_tokens`、`total_tokens`
  - 补齐时，输入 token 继续优先取预计算请求指标；拿不到时再按原始请求内容计算；输出 token 继续按最终响应内容计算
  - 4xx 错误场景下，当前继续不计算输入 token；`408` 超时不走这条归零口径
  - `stream_options.include_usage=true` 时，当前继续允许在 `[DONE]` 前追加 usage chunk；没有该 chunk 也不等于 usage 语义不存在
- **流式完成与异常语义**
  - `stream=true` 时，正常结束继续以字符串 `[DONE]` 作为终止信号，不能改成 `message_stop`、`response.completed` 或其他结束协议
  - 一旦已经开始向客户端发送 completion 流式数据帧，后续失败继续通过流内错误帧表达，不能回退成普通 HTTP JSON 错误体
  - 流式首包时间继续以第一个真正发送给客户端的应用层数据帧为准，而不是以 HTTP 连接建立时间为准
  - 超时、客户端中断、下游中断等异常场景，继续遵循“先记录当前 processData / requestId，再结束流”的收口原则
- **治理侧约定**
  - 非 private 请求会触发治理侧副作用，如并发计数
- **运行参数与待确认项**
  - 当前流式超时时间为 30 分钟

## 4. 回归验证基线

回归验证不以“内部实现是否一致”为标准，而以“外部协议、可观察行为、治理口径是否保持一致”为标准。至少应覆盖以下基线。

- **输入协议基线**
  - 能正常接收最小 chat completions 请求骨架
  - 能正常接收包含 `tools`、`tool_choice`、多轮 `tool_calls` / `tool_call_id` 的请求
  - 能正常接收 `content` 为字符串与内容块数组两种形式的消息
  - 能继续接受未列入固定字段的顶层扩展字段，而不是在入口直接拒绝
  - 特殊 Header 在入口仍能被收集并传递给后续处理链路
- **非流式输出基线**
  - 成功响应顶层字段继续保持 chat completion object 形态
  - `choices[].message`、`finish_reason`、`usage` 的基本结构与语义保持不变
  - `requestRiskData`、`sensitives` 在有值时仍能按现状出现在响应中
  - 上游缺少 `usage` 时，系统仍能按现状补齐 usage
- **流式输出基线**
  - `stream=true` 时返回 SSE `data:` 事件流，而不是普通 JSON 响应
  - 首包、增量包、风险结果包、usage 包、结束包的输出形态与顺序语义保持兼容
  - 正常结束仍以 `[DONE]` 表示
  - 已开始流式输出后，如后续失败，仍通过流内错误帧结束，而不是回退成 HTTP JSON 错误体
- **错误协议基线**
  - 同步错误继续返回统一 `error` object
  - HTTP 状态码与 `error.httpCode` 保持一致
  - 流式错误继续以 SSE `data:` 错误帧表达，并在错误后直接结束连接
- **行为不变量基线**
  - 普通模式与 direct mode 的模型选择行为保持一致
  - 多模型 fallback 的顺序、截断规则、最终错误暴露规则保持一致
  - direct mode 下跳过 availability check、限流与 safety check 的行为保持一致
  - queue、function simulate、reasoning merge / split、风险结果插入等增强逻辑对外结果保持兼容
  - usage 计算与归零口径保持一致
  - 非 private 请求触发治理侧副作用的条件保持一致

## 5. 验证方式

- **契约样例回放**
  - 基于 Java 现网或基线环境沉淀一组最小但覆盖关键分支的请求样例，分别对 Java 与 Go 发起请求
  - 对非流式场景按 JSON 结构比对关键字段、字段有无、字段类型、关键语义值
  - 对流式场景按 SSE 帧序列比对事件通道、chunk 形态、结束信号与错误信号
- **黄金样本比对**
  - 为最小请求、工具调用请求、流式请求、流式带 usage 请求、同步错误请求、流式错误请求建立黄金样本
  - Go 输出允许在时间戳、ID、上游自然波动文本上存在可控差异，但不允许协议形态和关键语义漂移
- **行为开关覆盖验证**
  - 分别验证 `X-BELLA-DIRECT`、`X-BELLA-MODEL`、`X-BELLA-FUNCTION-SIMULATE`、`X-BELLA-MERGE-REASONING`、`X-BELLA-SPLIT-REASONING` 等开关
  - 验证各开关打开前后，变化只发生在预期范围内
- **异常路径验证**
  - 验证限流、超时、上游失败、安全检查拦截、客户端中断等场景
  - 验证这些场景下的错误表达方式、结束方式、usage 口径与日志收口语义不变
- **治理与计量验证**
  - 通过可观察日志、计费记录、并发计数、requestId / processData 关联结果，验证治理侧副作用未漂移
  - 重点验证非 private 请求是否仍命中并发治理，4xx 与 `408` 的 usage 口径是否仍符合现状
- **分层验证建议**
  - 先做协议层回归：验证入参与出参契约
  - 再做链路层回归：验证 direct mode、fallback、queue、simulate、reasoning 等执行路径
  - 最后做观测层回归：验证 usage、日志、治理副作用、异常收口

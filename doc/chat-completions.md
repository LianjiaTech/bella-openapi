#  智能问答接口文档

## 目录

- [接口描述](#接口描述)
- [请求](#请求)
  - [HTTP 请求](#http-请求)
  - [请求体](#请求体)
  - [Message对象类型](#message对象类型)
  - [工具和函数定义](#工具和函数定义)
  - [消息内容格式](#消息内容格式)
- [响应](#响应)
  - [非流式响应](#非流式响应)
  - [流式响应](#流式响应)
  - [reasoning_content 字段](#reasoning_content-字段)
  - [响应参数](#响应参数)
- [错误码](#错误码)
- [示例](#示例)
  - [基本请求示例](#基本请求示例)
  - [工具调用请求示例](#工具调用请求示例)
  - [工具调用响应示例](#工具调用响应示例)
  - [工具结果提交示例](#工具结果提交示例)
  - [工具结果响应示例](#工具结果响应示例)
  - [图片输入示例](#图片输入示例)
  - [流式工具调用示例](#流式工具调用示例)

## 接口描述

创建一个聊天完成请求，支持流式和非流式响应。该接口接收一系列消息作为输入，并返回模型生成的完成内容。
本协议在OpenAI的Chat Completions API基础上扩展，支持了reasoning_content字段。最新的完整的OpenAI Chat Completions API参数可以参考[OpenAI Chat Completions API](https://platform.openai.com/docs/api-reference/chat/create)。

## 请求

### HTTP 请求

```http
POST /v1/chat/completions
```

### 请求体

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | --- | --- |
| model | string | 是 | 要使用的模型 ID |
| messages | array | 是 | 包含对话历史的消息数组。根据使用的模型不同，支持不同的消息类型（模态），如文本、图像和音频 |
| tools | array | 否 | 模型可以调用的工具列表。目前仅支持函数作为工具。使用此参数提供模型可能生成JSON输入的函数列表。最多支持128个函数 |
| tool_choice | string/object | 否 | 控制模型是否调用工具。<br>- `"none"`: 模型不会调用任何工具，而是生成消息<br>- `"auto"`: 模型可以在生成消息或调用一个或多个工具之间选择<br>- `"required"`: 模型必须调用一个或多个工具<br>- 也可以通过对象指定特定工具：`{"type": "function", "function": {"name": "my_function"}}`<br><br>当没有工具时，默认为`"none"`；有工具时，默认为`"auto"` |
| temperature | number | 否 | 采样温度，默认为 1。较低的值使输出更加确定性 |
| top_p | number | 否 | 核采样的概率质量，默认为 1 |
| n | integer | 否 | 为每个输入消息生成的聊天完成数量，默认为 1 |
| stream | boolean | 否 | 是否启用流式响应，默认为 false |
| stream_options | object | 否 | 流式响应选项，仅在 stream=true 时设置 |
| stop | string/array | 否 | 最多 4 个序列，API 将在生成这些序列时停止 |
| max_tokens | integer | 否 | 生成的最大令牌数 |
| presence_penalty | number | 否 | 存在惩罚，范围 -2.0 到 2.0，默认为 0 |
| frequency_penalty | number | 否 | 频率惩罚，范围 -2.0 到 2.0，默认为 0 |
| logit_bias | object | 否 | 修改指定令牌出现在完成中的可能性 |
| response_format | object | 否 | 指定模型必须输出的格式 |
| seed | integer | 否 | 用于确定性采样的种子值 |
| parallel_tool_calls | boolean | 否 | 是否允许并行工具调用 |
| user | string | 否 | 表示最终用户的唯一标识符 |

### Message对象类型

#### Developer 消息（开发者消息）

```json
{
  "role": "developer",
  "content": "你是一个有帮助的助手，专注于回答用户的问题。"
}
```

开发者提供的指令，无论用户发送什么消息，模型都应遵循。在较新的OpenAI模型（如o1系列）中，开发者消息替代了之前的系统消息。

#### System 消息（系统消息）

```json
{
  "role": "system",
  "content": "你是一个有帮助的助手，专注于回答用户的问题。"
}
```

开发者提供的指令，无论用户发送什么消息，模型都应遵循。在较新的OpenAI模型（如o1系列）中，建议使用开发者消息代替系统消息。

#### User 消息（用户消息）

```json
{
  "role": "user",
  "content": "你好，请介绍一下自己。"
}
```

终端用户发送的消息，包含提示或额外的上下文信息。

#### Assistant 消息（助手消息）

```json
{
  "role": "assistant",
  "content": "我是一个AI助手，可以回答问题和提供信息。"
}
```

模型响应用户消息发送的消息。

#### Tool 消息（工具消息）

```json
{
  "role": "tool",
  "content": "{\"temperature\":32,\"unit\":\"celsius\",\"description\":\"晴朗\",\"humidity\":45}",
  "tool_call_id": "call_abc123"
}
```

工具消息包含工具调用的结果，必须包含以下字段：
- `content`: 工具消息的内容（字符串）
- `role`: 消息作者的角色，在这种情况下为"tool"
- `tool_call_id`: 此消息响应的工具调用ID

### 工具和函数定义

#### Tool 对象

```json
{
  "type": "function",
  "function": {
    "name": "get_weather",
    "description": "获取指定城市的天气信息",
    "parameters": {
      "type": "object",
      "properties": {
        "location": {
          "type": "string",
          "description": "城市名称，如北京、上海"
        },
        "unit": {
          "type": "string",
          "enum": ["celsius", "fahrenheit"],
          "description": "温度单位"
        }
      },
      "required": ["location"]
    },
    "strict": true
  }
}
```

工具对象包含以下字段：
- `type`: 工具类型，目前仅支持"function"
- `function`: 函数定义，包含以下字段：
  - `name`: 函数名称（必填）。必须是a-z、A-Z、0-9或包含下划线和破折号，最大长度为64
  - `description`: 函数功能描述（可选）。模型用它来决定何时以及如何调用函数
  - `parameters`: 函数接受的参数，描述为JSON Schema对象（可选）
  - `strict`: 是否在生成函数调用时启用严格的模式遵循（可选，默认为false）

#### Tool Choice 对象

用于指定特定工具：

```json
{
  "type": "function",
  "function": {
    "name": "get_weather"
  }
}
```

### 消息内容格式

#### 文本内容

```json
{
  "role": "user",
  "content": "你好，请介绍一下自己。"
}
```

#### 图片内容

```json
{
  "role": "user",
  "content": [
    {
      "type": "text",
      "text": "这张图片是什么内容？"
    },
    {
      "type": "image_url",
      "image_url": {
        "url": "https://example.com/image.jpg",
        "detail": "high"
      }
    }
  ]
}
```

#### 工具调用内容

```json
{
  "role": "assistant",
  "content": null,
  "tool_calls": [
    {
      "id": "call_abc123",
      "type": "function",
      "function": {
        "name": "get_weather",
        "arguments": "{\"location\":\"北京\",\"unit\":\"celsius\"}"
      }
    }
  ]
}
```

## 响应

### 非流式响应

```json
{
  "id": "chatcmpl-123",
  "object": "chat.completion",
  "created": 1677652288,
  "model": "gpt-4o",
  "system_fingerprint": "fp_44709d6fcb",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "你好！我能帮你什么忙吗？",
      "reasoning_content": "用户用中文问候，我应该用中文回复。"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 9,
    "completion_tokens": 12,
    "total_tokens": 21
  }
}
```

### 流式响应

当 `stream=true` 时，服务器将发送一系列 Server-Sent Events (SSE)，每个事件包含部分响应。每个事件的格式如下：

```
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"reasoning_content":"用户用中文问候，"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"reasoning_content":"我应该用中文回复。"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"content":"你"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"content":"好"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

### reasoning_content 字段

Bella OpenAPI 在标准 OpenAI 接口基础上扩展了 `reasoning_content` 字段，用于提供模型的推理过程：

- 只有输出推理过程的模型才会返回 `reasoning_content` 字段
- 在非流式响应中，`reasoning_content` 作为 Message 对象的一个属性返回
- 在流式响应中，`reasoning_content` 通过 delta 对象分块返回
- 推理内容可以帮助开发者理解模型的思考过程和决策依据

### 响应参数

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| id | string | 响应的唯一标识符 |
| object | string | 对象类型，通常为 "chat.completion" 或 "chat.completion.chunk" |
| created | integer | 响应创建的时间戳 |
| model | string | 使用的模型 |
| system_fingerprint | string | 模型运行的后端配置指纹 |
| choices | array | 完成选项数组 |
| usage | object | 令牌使用统计 |

#### Choice 对象

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| index | integer | 选项的索引 |
| message | object | 在非流式响应中，包含完整的消息 |
| delta | object | 在流式响应中，包含消息的增量部分 |
| finish_reason | string | 完成原因，可能的值有 "stop"、"length"、"tool_calls"、"content_filter" 或 null |

#### Message/Delta 对象

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| role | string | 消息的角色，通常为 "assistant" |
| content | string | 消息的内容 |
| reasoning_content | string | 包含模型的推理过程 |
| tool_calls | array | 工具调用列表 |

#### Usage 对象

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| prompt_tokens | integer | 提示使用的令牌数 |
| completion_tokens | integer | 完成使用的令牌数 |
| total_tokens | integer | 总共使用的令牌数 |

## 错误码

| 错误码 | 描述 |
| --- | --- |
| 400 | 请求参数错误 |
| 401 | 认证失败，无效的 API 密钥 |
| 403 | 权限不足，API 密钥没有权限访问请求的资源 |
| 404 | 请求的资源不存在 |
| 429 | 请求过多，超出速率限制 |
| 500 | 服务器内部错误 |
| 503 | 服务暂时不可用 |

## 示例

### 基本请求示例

```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "system",
      "content": "你是一个有帮助的助手。"
    },
    {
      "role": "user",
      "content": "你好，请介绍一下自己。"
    }
  ],
  "temperature": 0.7,
  "stream": false
}
```

### 工具调用请求示例

```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "user",
      "content": "北京今天的天气怎么样？"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "获取指定城市的天气信息",
        "parameters": {
          "type": "object",
          "properties": {
            "location": {
              "type": "string",
              "description": "城市名称，如北京、上海"
            },
            "unit": {
              "type": "string",
              "enum": ["celsius", "fahrenheit"],
              "description": "温度单位"
            }
          },
          "required": ["location"]
        },
        "strict": true
      }
    }
  ],
  "tool_choice": "auto"
}
```

### 工具调用响应示例

当模型决定调用工具时，响应将包含 `tool_calls` 字段：

```json
{
  "id": "chatcmpl-123",
  "object": "chat.completion",
  "created": 1677652288,
  "model": "gpt-4o",
  "system_fingerprint": "fp_44709d6fcb",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": null,
      "reasoning_content": "用户询问北京的天气，我需要调用",
      "tool_calls": [
        {
          "id": "call_abc123",
          "type": "function",
          "function": {
            "name": "get_weather",
            "arguments": "{\"location\":\"北京\",\"unit\":\"celsius\"}"
          }
        }
      ]
    },
    "finish_reason": "tool_calls"
  }],
  "usage": {
    "prompt_tokens": 82,
    "completion_tokens": 25,
    "total_tokens": 107
  }
}
```

### 工具结果提交示例

在获取工具执行结果后，需要将结果提交回对话：

```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "user",
      "content": "北京今天的天气怎么样？"
    },
    {
      "role": "assistant",
      "content": null,
      "tool_calls": [
        {
          "id": "call_abc123",
          "type": "function",
          "function": {
            "name": "get_weather",
            "arguments": "{\"location\":\"北京\",\"unit\":\"celsius\"}"
          }
        }
      ]
    },
    {
      "role": "tool",
      "tool_call_id": "call_abc123",
      "name": "get_weather",
      "content": "{\"temperature\":32,\"unit\":\"celsius\",\"description\":\"晴朗\",\"humidity\":45}"
    }
  ]
}
```

### 工具结果响应示例

模型处理工具结果后的响应：

```json
{
  "id": "chatcmpl-456",
  "object": "chat.completion",
  "created": 1677652290,
  "model": "gpt-4o",
  "system_fingerprint": "fp_44709d6fcb",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "北京今天的天气晴朗，气温32°C，湿度45%。天气较热，建议做好防晒措施，多补充水分。",
      "reasoning_content": "根据天气API返回的数据，北京今天天气晴朗，温度32摄氏度，湿度45%。这属于较热的天气，应该提醒用户注意防晒和补水。"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 110,
    "completion_tokens": 42,
    "total_tokens": 152
  }
}
```

### 图片输入示例

支持在用户消息中包含图片：

```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "这张图片是什么内容？"
        },
        {
          "type": "image_url",
          "image_url": {
            "url": "https://example.com/image.jpg",
            "detail": "high"
          }
        }
      ]
    }
  ]
}
```

#### 图片URL格式

图片URL可以是以下格式之一：

1. 互联网可访问的URL：
   ```json
   {
     "type": "image_url",
     "image_url": {
       "url": "https://example.com/image.jpg"
     }
   }
   ```

2. Base64编码的图片数据：
   ```json
   {
     "type": "image_url",
     "image_url": {
       "url": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL..."
     }
   }
   ```

3. 图片细节级别：
   可以通过 `detail` 参数指定图片分析的细节级别，可选值为：
   - `"high"`：高细节分析，适用于需要识别细小文本或细节的场景
   - `"low"`：低细节分析，处理速度更快，消耗的令牌更少
   - 不指定时默认为 `"auto"`

### 图片输入响应示例

```json
{
  "id": "chatcmpl-789",
  "object": "chat.completion",
  "created": 1677652295,
  "model": "gpt-4o",
  "system_fingerprint": "fp_44709d6fcb",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "这张图片展示了一只橙色的猫咪坐在窗台上，望向窗外。窗外可以看到一些绿色的树木和蓝色的天空。猫咪看起来很放松，尾巴卷曲在身体旁边。",
      "reasoning_content": "图片中有一只橙色的猫，它坐在窗台上看向窗外。我可以看到窗外有树木和天空。猫咪的姿势显示它很放松。我应该详细描述我看到的内容，包括猫的颜色、位置、周围环境和姿态。"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 1042,
    "completion_tokens": 65,
    "total_tokens": 1107
  }
}
```

### 流式工具调用示例

当在流式模式下使用工具调用时，响应会分多个事件返回：

```
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"reasoning_content":"用户询问北京的天气，我需要调用"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"reasoning_content":"天气查询函数来获取这一信息。"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_abc123","type":"function","function":{"name":"get_weather"}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\""}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"location"}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\":\""}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"北京"}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\",\""}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"unit"}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\":\""}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"celsius"}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"}"}}]},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-4o","system_fingerprint":"fp_44709d6fcb","usage":{"prompt_tokens": 1042,"completion_tokens": 65,"total_tokens": 1107},"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

data: [DONE]
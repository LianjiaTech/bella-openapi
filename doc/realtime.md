# Bella OpenAPI 实时语音对话接口文档

## 目录

- [1. 概述](#1-概述)
  - [1.1 功能特性](#11-功能特性)
  - [1.2 应用场景](#12-应用场景)
- [2. 接口规格](#2-接口规格)
  - [2.1 基本信息](#21-基本信息)
  - [2.2 请求参数](#22-请求参数)
    - [2.2.1 URL 参数](#221-url-参数)
    - [2.2.2 WebSocket 启动消息](#222-websocket-启动消息)
    - [2.2.3 音频数据格式](#223-音频数据格式)
  - [2.3 响应消息类型](#23-响应消息类型)
    - [2.3.1 转写相关消息](#231-转写相关消息)
    - [2.3.2 LLM 相关消息](#232-llm-相关消息)
    - [2.3.3 语音合成相关消息](#233-语音合成相关消息)
    - [2.3.4 VAD 相关消息](#234-vad-相关消息)
    - [2.3.5 会话控制和错误消息](#235-会话控制和错误消息)
- [3. 接入指南](#3-接入指南)
  - [3.1 Java 接入示例](#31-java-接入示例)
  - [3.2 JavaScript 接入示例](#32-javascript-接入示例)
  - [3.3 可用的语音合成音色](#33-可用的语音合成音色)
- [4. 错误处理](#4-错误处理)
- [5. 最佳实践](#5-最佳实践)

## 1. 概述

实时语音对话接口提供了一站式的语音识别、大模型对话和语音合成能力，支持用户通过麦克风进行实时语音输入，系统自动识别语音内容，调用大模型生成回复，并将回复内容转换为语音输出。整个过程通过 WebSocket 协议实现，具有低延迟、高实时性的特点。

### 1.1 功能特点

- **实时语音识别**：支持用户实时语音输入，并将语音转换为文本
- **流式大模型对话**：将识别的文本发送给大模型，获取流式回复
- **语音合成**：将大模型回复转换为语音，实现自然的语音对话体验
- **全双工通信**：基于 WebSocket 协议，支持全双工通信，实现真正的实时对话

### 1.2 应用场景

- 智能语音助手
- 客服机器人
- 语音交互系统
- 无障碍应用
- 车载语音系统

## 2. 接口规格

### 2.1 基本信息

- **接口路径**：`/v1/audio/realtime` 或 `/v1/audio/asr/stream`（只返回转录事件）
- **协议**：WebSocket
- **请求方式**：GET
- **认证方式**：Bearer Token（通过请求头 `Authorization` 传递）

### 2.2 请求参数

#### 2.2.1 URL 参数

| 参数名 | 类型 | 必选 | 描述 |
| --- | --- | --- | --- |
| model | string | 否 | 指定使用的模型，不指定则使用能力点的默认模型 |

#### 2.2.2 WebSocket 启动消息

连接建立后，客户端需要发送一个 JSON 格式的启动消息：
`/v1/audio/realtime`，启动消息如下：
`/v1/audio/asr/stream` 不需要 `llm_option` 和 `tts_option` 参数

##### StartTranscription 消息

```json
{
  "header": {
    "message_id": "uuid-string",
    "task_id": "uuid-string",
    "namespace": "SpeechTranscriber",
    "name": "StartTranscription",
    "appkey": "default"
  },
  "payload": {
    "format": "pcm",
    "sample_rate": 16000,
    "enable_intermediate_result": true,
    "enable_punctuation_prediction": true,
    "enable_inverse_text_normalization": true,
    "llm_option": {
      "main": {
        "model": "qwen2.5-coder:3b",
        "sys_prompt": "你是一个全能的语音助理，你的回复会转成音频给用户，所以请尽可能简洁的回复，同时首句话尽快结束以便更好的进行流式合成语音"
      }
    },
    "tts_option": {
      "model": "chat-tts",
      "sample_rate": 24000
    }
  }
}
```

参数说明：

- **header**: 消息头部信息
  - **message_id**: 消息唯一标识，建议使用 UUID
  - **task_id**: 任务唯一标识，建议使用 UUID
  - **namespace**: 固定为 "SpeechTranscriber"
  - **name**: 固定为 "StartTranscription"
  - **appkey**: 固定为 "default"

- **payload**: 任务配置参数
  - **format**: 音频格式，固定为 "pcm"
  - **sample_rate**: 音频采样率，推荐 16000
  - **enable_intermediate_result**: 是否启用中间结果，建议设为 true
  - **enable_punctuation_prediction**: 是否启用标点预测，建议设为 true
  - **enable_inverse_text_normalization**: 是否启用逆文本规范化，建议设为 true
  - **llm_option**: 大模型选项（可选）
    - **main**: 主模型配置
      - **model**: 模型名称，如 "qwen2.5-coder:3b"
      - **sys_prompt**: 系统提示词，用于设置模型的行为和角色，可以使用 Python Jinja2 模板
      - **prompt**: 用户提示词，可以提供 Python Jinja2 模板，通过上下文改写用户消息传给大模型
      - **temperature**: 温度参数，控制输出的随机性，值越大随机性越高，取值范围 [0.0, 2.0]，默认为 1.0
    - **workers**: 工作者模型配置列表（可选），每个工作者模型包含以下属性
      - **model**: 模型名称
      - **blocking**: 是否阻塞，默认为 false
      - **variable_name**: 结果变量名
      - **variable_type**: 变量类型
      - **sys_prompt**: 系统提示词，可以使用 Python Jinja2 模板
      - **prompt**: 用户提示词，可以使用 Python Jinja2 模板
      - **temperature**: 温度参数，默认为 1.0
      - **json_schema**: JSON 模式定义
  - **tts_option**: 语音合成选项（可选）
    - **model**: 语音合成模型，如 "chat-tts"
    - **sample_rate**: 合成音频采样率，推荐 24000
    - **voice**: 合成音色（可选），具体可用音色取决于所选模型
  - **variables**: 变量配置（可选）map，结合prompt模版使用

##### 配置示例

以下是一个包含主模型和工作者模型的完整配置示例，展示了如何使用 Jinja2 模板和变量进行消息改写：

```json
{
  "llm_option": {
    "main": {
      "model": "qwen2.5-coder:3b",
      "sys_prompt": "你是一个全能的语音助理，你的回复会转成音频给用户，所以请尽可能简洁的回复，同时首句话尽快结束以便更好的进行流式合成语音。"
    },
    "workers": [
      {
        "model": "qwen2.5-coder:3b",
        "blocking": true,
        "variable_name": "rewrite_user_message",
        "sys_prompt": "你是一个user message rewrite专家，负责将基于voice转录的用户消息进行改写，让其更通顺和连贯，同时完成必须的指代消歧。不要解释，直接改写，如果不需要改写，则直接输出用户的原始内容",
        "prompt": "## 用户历史对话\n<history_messages>\n{% for message in history_messages %}\n{% if message.role == \"user\" %}\n  <user timestamp=\"{{loop.index}}\">{{{ message.content }}}</user>\n{% elif message.role == \"assistant\" %}\n  <agent timestamp=\"{{loop.index}}\">{{{ message.content }}}</agent>\n{% endif %}\n{% endfor %}\n\n</history_messages>\n\n## 用户最新消息\n<user_message>\n  {{user_message}}\n</user_message>\n\n请进行消息改写吧！\n"
      }
    ]
  },
  "tts_option": {
    "model": "chat-tts",
    "voice": "zh_female",
    "sample_rate": 24000
  }
}
```

该配置示例展示了：
1. 主模型配置：使用 qwen2.5-coder:3b 模型与用户进行对话
2. 工作者模型配置：使用相同模型作为工作者，负责改写用户消息
3. 工作者模型使用 Jinja2 模板处理历史消息和当前用户消息
4. TTS 配置：指定了模型、音色和采样率

##### StopTranscription 消息

当需要结束转写任务时，客户端需要发送一个 StopTranscription 消息：
服务端处理完未完成的消息后，会返回SessionClose消息。

```json
{
  "header": {
    "message_id": "uuid-string",
    "task_id": "uuid-string",
    "namespace": "SpeechTranscriber",
    "name": "StopTranscription",
    "appkey": "default"
  },
  "payload": {}
}
```

#### 2.2.3 音频数据

启动消息发送后，客户端需要持续发送二进制格式的音频数据。音频数据要求：

- 格式：PCM（脉冲编码调制）
- 采样率：16000Hz
- 位深度：16位
- 通道数：单通道（mono）
- 每帧大小：推荐 3200 字节（对应 100ms 的音频）

### 2.3 响应消息

服务器会通过 WebSocket 连接发送多种类型的文本消息和二进制数据。文本消息为 JSON 格式，主要包含以下类型：

#### 2.3.1 转写相关消息

- **TranscriptionStarted**: 转写任务开始
- **SentenceBegin**: 检测到语音开始
- **TranscriptionResultChanged**: 转写结果更新
- **SentenceEnd**: 语音句子结束，包含最终转写结果
- **TranscriptionCompleted**: 转写任务完成
- **TranscriptionFailed**: 转写任务失败

示例：
```json
{
  "header": {
    "message_id": "uuid-string",
    "task_id": "uuid-string",
    "namespace": "SpeechTranscriber",
    "name": "TranscriptionResultChanged",
    "status": 20000000
  },
  "payload": {
    "result": "你好，我想了解一下今天的天气怎么样"
  }
}
```

#### 2.3.2 大模型相关消息

- **LLM_CHAT_BEGIN**: 大模型开始生成回复
- **LLM_CHAT_DELTA**: 大模型回复内容更新
- **LLM_CHAT_END**: 大模型回复结束
- **LLM_CHAT_CANCELLED**: 大模型回复被取消

示例：
```json
{
  "header": {
    "message_id": "uuid-string",
    "task_id": "uuid-string",
    "namespace": "SpeechTranscriber",
    "name": "LLM_CHAT_DELTA",
    "status": 20000000
  },
  "payload": {
    "data": "你好！今天北京天气晴朗，气温22°C到28°C，空气质量良好，是个适合户外活动的好天气。"
  }
}
```

#### 2.3.3 语音合成相关消息

- **TTS_BEGIN**: 语音合成开始
- **TTS_TTFT**: 首个音频数据包就绪（Time To First Token）
- **TTS_DELTA**: 语音合成增量数据
- **TTS_END**: 语音合成结束

语音合成的音频数据通过二进制消息发送，格式为 PCM，采样率为 24000Hz，16位，单通道。

#### 2.3.4 VAD 相关消息（语音活动检测）

- **VOICE_QUIET**: 检测到静音
- **VOICE_STARTING**: 检测到开始说话
- **VOICE_SPEAKING**: 检测到正在说话
- **VOICE_STOPPING**: 检测到停止说话
- **VOICE_PAUSING**: 检测到说话暂停

示例：
```json
{
  "header": {
    "message_id": "uuid-string",
    "task_id": "uuid-string",
    "namespace": "SpeechTranscriber",
    "name": "VOICE_SPEAKING",
    "status": 20000000,
    "status_message": "GATEWAY|SUCCESS|Success."
  },
  "payload": {
    "start_frame": 11,
    "end_frame": 29,
    "duration": 576,
    "data": null,
    "timestamp": 1744612674.163157,
    "latency": -1
  }
}
```

#### 2.3.5 会话控制和错误消息

- **SESSION_CLOSE**: 会话关闭，客户端发送StopTranscription消息后，服务端处理完未完成的消息后，会返回SessionClose消息。
- **TASK_FAILED**: 任务失败
- **LLM_CHAT_ERROR**: LLM 聊天错误
- **LLM_WORKER_ERROR**: LLM 子任务错误

示例：
```json
{
  "header": {
    "message_id": "uuid-string",
    "task_id": "uuid-string",
    "namespace": "SpeechTranscriber",
    "name": "SESSION_CLOSE",
    "status": 20000000
  },
  "payload": {}
}
```

## 3. 接入指南

### 3.1 Java 接入示例

实时语音接口的 Java 实现可以参考项目中的 [RealtimeDemo.java](/api/server/src/test/java/com/ke/bella/openapi/RealtimeDemo.java) 类。

该示例展示了如何使用 OkHttp 库实现 WebSocket 连接，并完成以下功能：

1. 建立 WebSocket 连接并发送 StartTranscription 消息
2. 发送音频数据（PCM 格式）
3. 处理各种事件类型（转写、VAD、LLM、TTS 等）
4. 接收和处理 TTS 音频数据
5. 发送 StopTranscription 消息结束会话

示例中包含了完整的错误处理和资源释放逻辑，可以作为实现的参考。


### 3.2 JavaScript 接入示例

实时语音接口的 JavaScript 实现可以参考项目中的 [RealtimeAudioRecorder.ts](/web/src/components/playground/RealtimeAudioRecorder.ts) 类。


### 3.3 可用的语音合成音色
不同模型可用的音色不同，详情请访问bella-openapi主页获取。

## 4. 错误处理

### 4.1 常见错误码

| 错误码 | 描述 | 解决方案 |
| --- | --- | --- |
| 401 | 未授权 | 检查API Key是否正确 |
| 403 | 禁止访问 | 检查API Key权限 |
| 429 | 请求过多 | 降低请求频率 |
| 500 | 服务器内部错误 | 联系技术支持 |

### 4.2 WebSocket 连接错误

| 错误类型 | 可能原因 | 解决方案 |
| --- | --- | --- |
| 连接失败 | 网络问题或服务不可用 | 检查网络连接，稍后重试 |
| 连接关闭 | 服务器关闭连接或网络中断 | 实现自动重连机制 |
| 消息解析错误 | 无效的JSON格式 | 检查消息格式 |

## 5. 最佳实践

### 5.1 音频质量优化

- 使用高质量麦克风，减少环境噪音
- 保持适当的音量和距离
- 使用降噪和回声消除技术
- 确保音频采样率和格式符合要求

### 5.2 性能优化

- 实现断线重连机制
- 优化音频数据处理和发送逻辑
- 使用 WebWorker 处理音频数据，避免阻塞主线程
- 实现缓冲机制，平滑处理网络波动

### 5.3 用户体验优化

- 提供清晰的录音状态指示
- 显示实时转写结果，提供视觉反馈
- 实现语音活动检测，自动开始和结束录音
- 提供音量可视化，帮助用户调整音量

## 6. 示例代码

完整的前端实现示例可以参考 Bella OpenAPI 项目中的以下文件：

- `web/src/app/playground/v1/audio/realtime/page.tsx`: 实时语音对话页面
- `web/src/components/playground/RealtimeAudioRecorder.ts`: 实时语音录制器
- `web/src/components/playground/PCMPlayer.ts`: PCM音频播放器

## 7. 常见问题

### Q1: 为什么我的语音识别结果不准确？
A1: 可能是由于麦克风质量、环境噪音或网络问题导致。建议使用高质量麦克风，在安静的环境中使用，并确保网络连接稳定。

### Q2: 如何处理不同浏览器的兼容性问题？
A2: 使用 WebRTC adapter 库处理不同浏览器的兼容性问题，并在不支持的浏览器上提供友好的提示。

### Q3: 如何优化移动设备上的性能？
A3: 减小音频缓冲区大小，优化音频处理逻辑，使用更低的采样率，并实现电池优化策略。

### Q4: 如何处理网络不稳定的情况？
A4: 实现断线重连机制，使用音频缓冲策略，并在网络恢复后重新同步对话状态。

### Q5: 系统支持哪些语言？
A5: 支持的语言取决于所选模型。请参考模型文档了解具体支持的语言列表。

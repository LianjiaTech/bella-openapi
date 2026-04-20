# Task 1 / Audio 契约盘点

## 1. 范围说明

本文从总表中拆出 `Audio` 能力点，覆盖 `/v1/audio/*` 相关能力在 Go 重构前必须保持兼容的输入输出协议、行为不变量、回归基线与验证方式。

## 2. 协议

### 2.1 TTS / Speech

#### 2.1.1 `POST /v1/audio/speech` 输入协议

##### 认证方式

- 默认使用 `Authorization: Bearer ...`

##### Content-Type

- 请求默认是 `application/json`

##### 请求体结构

- 顶层是 JSON object
- 核心字段：`model`、`input`、`voice`、`response_format`、`speed`、`sample_rate`、`stream`、`speakers`
- 允许携带额外扩展字段

##### 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "tts-1",
    "input": "hello",
    "voice": "alloy",
    "response_format": "mp3",
    "stream": true
  }
  ```
- 多说话人场景仍应兼容在顶层携带 `speakers` 结构，而不是额外切到另一套 endpoint

#### 2.1.2 `POST /v1/audio/speech` 输出协议

##### 成功响应

- 响应 `Content-Type` 由 `response_format` 决定，当前支持 `pcm`、`mp3`、`wav`、`aac`、`ogg`、`opus`、`flac`、`m4a`
- 非流式响应返回原始音频字节流，例如 `audio/mpeg`、`audio/wav`、`audio/ogg`
- 流式响应继续返回原始音频字节流，不是 SSE，也不存在 `[DONE]`

##### 错误响应

- 失败时继续返回统一错误体，而不是返回音频字节流中的私有错误片段
- 最小错误骨架仍应兼容：
  ```json
  {
    "error": {
      "code": "400",
      "httpCode": 400,
      "message": "...",
      "type": "Illegal Argument"
    }
  }
  ```

### 2.2 Realtime ASR

#### 2.2.1 `GET/WS /v1/audio/realtime` 输入协议

##### 建连条件

- 必须是 WebSocket upgrade 请求
- 非 WebSocket 请求当前直接返回 `400`

##### 请求协议

- `model` 通过 query parameter 传入，当前可为空
- 文本帧使用 `RealTimeMessage` 结构：
  - `header.name` 表示事件类型
  - `header.task_id` 可由客户端传入
  - `payload` 承载音频格式、采样率、热词、VAD、LLM/TTS 选项等
- 当前支持的客户端控制事件至少包括：
  - `StartTranscription`
  - `StopTranscription`
- `ping` 文本消息继续返回 `pong`
- 二进制帧用于承载音频数据
- 最小启动消息仍应兼容：
  ```json
  {
    "header": { "name": "StartTranscription", "task_id": "task_x" },
    "payload": { "format": "pcm", "sample_rate": 16000 }
  }
  ```

#### 2.2.2 `GET/WS /v1/audio/realtime` 输出协议

##### 成功响应

- 服务端继续返回 JSON 文本帧，而不是 SSE
- 最小启动响应仍应兼容：
  ```json
  {
    "header": { "name": "TranscriptionStarted", "task_id": "task_x" }
  }
  ```
- 关键事件至少包括：
  - `TranscriptionStarted`
  - `SentenceBegin`
  - `TranscriptionResultChanged`
  - `SentenceEnd`
  - `TranscriptionCompleted`
- 当启用更高事件级别或链路扩展能力时，当前还可能出现 `VOICE_*`、`LLM_*`、`TTS_*`、`SESSION_CLOSE` 等事件

##### 错误响应

- 错误响应使用 `TaskFailed`
- 错误响应体同时包含 `header.status`、`header.status_message` 与顶层 `error`
- 错误消息最小骨架仍应兼容：
  ```json
  {
    "header": {
      "name": "TaskFailed",
      "task_id": "task_x",
      "status": 400,
      "status_message": "..."
    },
    "error": {
      "code": "400",
      "httpCode": 400,
      "message": "..."
    }
  }
  ```

#### 2.2.3 `GET/WS /v1/audio/asr/stream` 输入协议

##### 建连条件

- 必须是 WebSocket upgrade 请求
- 非 WebSocket 请求当前直接返回 `400`

##### 请求协议

- `model` 通过 query parameter 传入，当前可为空
- 文本帧使用 `RealTimeMessage` 结构：
  - `header.name` 表示事件类型
  - `header.task_id` 可由客户端传入
  - `payload` 承载音频格式、采样率、热词、VAD、LLM/TTS 选项等
- 当前支持的客户端控制事件至少包括：
  - `StartTranscription`
  - `StopTranscription`
- `ping` 文本消息继续返回 `pong`
- 二进制帧用于承载音频数据
- 最小启动消息仍应兼容：
  ```json
  {
    "header": { "name": "StartTranscription", "task_id": "task_x" },
    "payload": { "format": "pcm", "sample_rate": 16000 }
  }
  ```

#### 2.2.4 `GET/WS /v1/audio/asr/stream` 输出协议

##### 成功响应

- 服务端继续返回 JSON 文本帧，而不是 SSE
- 最小启动响应仍应兼容：
  ```json
  {
    "header": { "name": "TranscriptionStarted", "task_id": "task_x" }
  }
  ```
- 关键事件至少包括：
  - `TranscriptionStarted`
  - `SentenceBegin`
  - `TranscriptionResultChanged`
  - `SentenceEnd`
  - `TranscriptionCompleted`
- 当启用更高事件级别或链路扩展能力时，当前还可能出现 `VOICE_*`、`LLM_*`、`TTS_*`、`SESSION_CLOSE` 等事件

##### 错误响应

- 错误响应使用 `TaskFailed`
- 错误响应体同时包含 `header.status`、`header.status_message` 与顶层 `error`
- 错误消息最小骨架仍应兼容：
  ```json
  {
    "header": {
      "name": "TaskFailed",
      "task_id": "task_x",
      "status": 400,
      "status_message": "..."
    },
    "error": {
      "code": "400",
      "httpCode": 400,
      "message": "..."
    }
  }
  ```

### 2.3 OpenAI Transcriptions

#### 2.3.1 `POST /v1/audio/transcriptions` 输入协议

##### 认证方式

- 默认使用 `Authorization: Bearer ...`

##### Content-Type

- 请求是 `multipart/form-data`
- 核心 part / field：`file`、`model`、`language`、`prompt`、`response_format`、`temperature`、`user`

##### 请求协议

- 最小请求骨架仍应保持为 multipart 表单，其中至少包含：
  - `file`
  - `model`
- `response_format` 当前继续兼容至少：
  - `json`
  - `verbose_json`
  - `text`
  - `srt`
  - `vtt`

#### 2.3.2 `POST /v1/audio/transcriptions` 输出协议

##### 成功响应

- 当 `response_format=json|verbose_json` 时，返回 OpenAI 风格 transcription JSON 结构，例如：
  ```json
  {
    "text": "hello world",
    "duration": 3.2,
    "segments": [
      { "id": 0, "start": 0.0, "end": 1.2, "text": "hello" }
    ]
  }
  ```
- 核心字段：`text`、`language`、`duration`、`words`、`segments`、`usage`
- `verbose_json` 时，当前继续允许返回 `segments`
- 当 `response_format=text|srt|vtt` 时，继续返回对应纯文本字幕内容，而不是 JSON envelope

##### 错误响应

- 失败时继续返回统一错误体

### 2.4 Flash ASR

#### 2.4.1 `POST /v1/audio/asr/flash` 输入协议

##### 认证方式

- 默认使用 `Authorization: Bearer ...`

##### Content-Type

- 请求体是原始音频字节流，当前不走 JSON body

##### 请求协议

- 请求体是原始音频字节流
- 参数主要通过 header 传递：
  - `format`
  - `sample_rate`
  - `max_sentence_silence`
  - `model`
  - `hot_words`
  - `hot_words_table_id`
  - `convert_numbers`
- 最小请求形态仍应保持为“header + 原始音频 body”，而不是改成 JSON：
  ```text
  POST /v1/audio/asr/flash
  format: wav
  sample_rate: 16000
  model: asr_x

  <binary audio bytes>
  ```

#### 2.4.2 `POST /v1/audio/asr/flash` 输出协议

##### 成功响应

- 返回 Flash ASR JSON 结构，例如：
  ```json
  {
    "task_id": "task_x",
    "user": "u1",
    "flash_result": {
      "duration": 3200,
      "sentences": [
        { "text": "hello", "begin_time": 0, "end_time": 1200 }
      ]
    }
  }
  ```
- 核心字段：`task_id`、`user`、`flash_result.duration`、`flash_result.sentences[]`
- `flash_result.sentences[]` 继续包含 `text`、`begin_time`、`end_time`

##### 错误响应

- 失败时继续返回统一错误体

### 2.5 Async Transcription Task

#### 2.5.1 `POST /v1/audio/transcriptions/file` 输入协议

##### 认证方式

- 默认使用 `Authorization: Bearer ...`

##### Content-Type

- 请求继续使用 `application/json`

##### 请求体结构

- `transcriptions/file` 请求体是 JSON object
- 核心字段：`url`、`model`、`user`、`callback_url`
- 还支持 `speaker_diarization`、`speaker_number`、`hot_word`、`language`、`audio_mode`、`sample_rate` 等转写参数
- 最小提交请求骨架仍应兼容：
  ```json
  {
    "url": "https://...",
    "model": "asr_x",
    "user": "u1",
    "callback_url": ""
  }
  ```

#### 2.5.2 `POST /v1/audio/transcriptions/file` 输出协议

##### 成功响应

- 提交成功后继续只返回任务提交结果，而不是同步转写结果
- 最小成功响应仍应兼容：
  ```json
  { "task_id": "task_x" }
  ```

##### 错误响应

- 缺少 `model`、`url`、`user` 等必要参数时继续报参数错误
- 上游入队失败时继续返回统一错误体，而不是伪造成功 `task_id`

#### 2.5.3 `POST /v1/audio/transcriptions/file/result` 输入协议

##### 认证方式

- 默认使用 `Authorization: Bearer ...`

##### Content-Type

- 请求继续使用 `application/json`

##### 请求体结构

- `transcriptions/file/result` 请求体核心字段是 `task_id` 数组
- 最小查询请求骨架仍应兼容：
  ```json
  { "task_id": ["task_x"] }
  ```

#### 2.5.4 `POST /v1/audio/transcriptions/file/result` 输出协议

##### 成功响应

- 查询成功响应继续返回 JSON object，而不是直接返回数组
- 最小成功响应仍应兼容：
  ```json
  { "data": [{ "...": "..." }] }
  ```
- `data[]` 元素当前可能是直接结果对象，也可能是仅包含 `file_id` 的对象
- 当返回完整结果对象时，当前至少继续兼容 `task_id`、`text`、`segments`、`language`、`duration` 等转写结果字段

##### 错误响应

- 缺少 `task_id` 或任务查询失败时继续报参数错误或统一错误体

### 2.6 Speaker 能力

#### 2.6.1 `POST /v1/audio/speaker/embedding` 输入协议

##### 认证方式

- 默认使用 `Authorization: Bearer ...`

##### Content-Type

- 请求继续使用 `application/json`

##### 请求体结构

- 请求体是 JSON object
- 核心字段：`url`、`base64`、`model`、`normalize`、`sample_rate`、`task_id`、`enable_vad`、`vad_aggressiveness`、`min_speech_duration`、`max_silence_duration`
- 最小请求骨架仍应兼容：
  ```json
  {
    "url": "https://...",
    "model": "speaker-embedding-x",
    "sample_rate": 16000
  }
  ```

#### 2.6.2 `POST /v1/audio/speaker/embedding` 输出协议

##### 成功响应

- 返回 JSON object
- Speaker Embedding 响应核心字段：`task`、`task_id`、`duration`、`dimensions`、`embeddings`
- `embeddings[]` 元素核心字段：`id`、`start`、`end`、`confidence`、`embedding`

##### 错误响应

- 失败时继续返回统一错误体

#### 2.6.3 `POST /v1/audio/speaker/diarization` 输入协议

##### 认证方式

- 默认使用 `Authorization: Bearer ...`

##### Content-Type

- 请求继续使用 `application/json`

##### 请求体结构

- 请求体当前复用异步转写的 JSON 结构
- 核心字段：`url`、`model`、`user`、`speaker_diarization`、`speaker_number`、`language`、`sample_rate`
- 最小请求骨架仍应兼容：
  ```json
  {
    "url": "https://...",
    "model": "speaker-diarization-x",
    "user": "u1",
    "speaker_diarization": true
  }
  ```

#### 2.6.4 `POST /v1/audio/speaker/diarization` 输出协议

##### 成功响应

- 返回 JSON object
- Speaker Diarization 响应核心字段：`task`、`task_id`、`language`、`duration`、`text`、`segments`、`num_speakers`、`speaker_embeddings`
- `segments[]` 元素核心字段：`start`、`end`、`text`、`channel_id`、`speaker_id`、`confidence`

##### 错误响应

- 失败时继续返回统一错误体

## 3. 不能改变的行为

### 3.1 TTS / Speech

- **模型与路由规则**
  - `model` 继续来自请求体
  - 请求继续按 `endpoint + model` 选路
- **输出格式规则**
  - `response_format` 为空时，继续回落到 channel 默认格式
  - 响应 `Content-Type` 继续与最终音频格式一一对应
- **流式规则**
  - `stream` 继续决定“单次返回完整音频”还是“持续写出音频字节”
  - `stream=true` 时继续是原始字节流，而不是 SSE / JSON chunk
  - `stream` 省略时当前默认仍按流式语义处理，这个默认值不能悄悄反转
- **usage / 计费口径**
  - TTS 当前继续按输入文本长度形成 usage / 计费基础，而不是按音频输出时长
  - 若存在预计算请求指标，继续优先使用预计算值；否则继续按请求中的 `input` 文本长度补齐
  - 当两者都不可用时，当前继续回落为 `0`

### 3.2 Realtime ASR

- **入口一致性**
  - `/v1/audio/realtime` 与 `/v1/audio/asr/stream` 当前共用同一套 WebSocket 协议与处理流程
- **会话启动规则**
  - 客户端必须先发送 `StartTranscription`，之后才能发送音频二进制帧
  - 若未启动任务就发送音频，继续返回错误
  - 若 `task_id` 缺失，服务端继续自动生成任务 ID
  - 单个 WebSocket 会话当前只允许一个在途转写任务
- **停止与关闭规则**
  - `StopTranscription` 继续作为显式停止指令
  - `task_id` 不匹配时继续返回错误
  - 正常完成时，WebSocket 继续在任务结束或会话结束时按协议关闭
  - 客户端连接关闭时，服务端继续联动关闭上游 ASR WebSocket
- **错误规则**
  - 不支持的文本事件继续返回错误，而不是静默忽略
  - 上游 ASR 连接断开或发送失败时，继续向客户端发送错误，再清理任务状态
  - WebSocket 建立后，后续失败继续通过协议内错误消息表达，不能退回 HTTP JSON 错误
  - 超时、客户端中断、下游中断等异常场景，继续遵循“先记录当前 processData / requestId，再结束连接”的收口原则
- **路由与治理规则**
  - 请求继续按实际 URI 与 `model` 路由
  - 非 private 请求继续触发并发计数等治理副作用
- **usage / 计费口径**
  - realtime 会话当前继续按会话持续时长形成 usage，而不是按消息条数或音频包数量计费
  - 该时长继续以请求开始到会话收口的持续时间为基础记录

### 3.3 OpenAI Transcriptions

- **能力映射规则**
  - `/v1/audio/transcriptions` 当前继续复用 Flash ASR 能力点实现，而不是独立走另一套路由能力
- **输入转换规则**
  - 音频格式继续优先从上传文件名推导
  - 上传文件继续在入口被转换为内部 `AsrRequest`
- **输出转换规则**
  - 上游 Flash ASR 结果继续在出口转换为 OpenAI transcription 响应
  - 对外不能泄漏 Flash ASR 原生结构

### 3.4 Flash ASR

- **入参来源规则**
  - `format`、`sample_rate`、`max_sentence_silence`、`model`、`hot_words`、`hot_words_table_id`、`convert_numbers` 继续来自 HTTP header，而不是 JSON body
  - 原始请求体继续直接作为音频内容读取
- **热词规则**
  - `hot_words` 继续在入口尝试做 URL decode
  - 解码失败时继续使用原始字符串，而不是直接报错
- **路由规则**
  - 请求继续按 `/v1/audio/asr/flash + model` 路由
- **usage / 收口口径**
  - Flash ASR 当前不对外返回统一 usage 结构，但收口侧仍继续保留既有计费口径
  - 其中错误场景当前继续留下最小 usage 记号参与日志 / 成本收口，不能把这类失败请求静默改成完全无 usage

### 3.5 Async Transcription Task

- **异步语义**
  - `transcriptions/file` 继续只负责入队并返回 `task_id`，而不是同步返回转写结果
  - `transcriptions/file/result` 继续作为批量取结果入口
- **参数校验规则**
  - `model`、`url`、`user` 继续是必填项
  - `callback_url` 为空时继续被归一化为空字符串，而不是报错
- **结果聚合规则**
  - 查询多个 `task_id` 时继续返回聚合后的 `data` 数组
  - 单个任务如果只有 `output_file_id`，继续包装为 `{file_id: ...}` 返回

### 3.6 Speaker 能力

- **路由规则**
  - `speaker/embedding` 与 `speaker/diarization` 继续作为两个独立能力点路由
  - 两者都继续按 `endpoint + model` 选路
- **协议边界**
  - `speaker/embedding` 继续保持 embedding 风格输出，而不是退化成普通转写结果
  - `speaker/diarization` 继续保持“文本 + 分段 + speaker 信息”的组合结构
- **输入约束**
  - `speaker/diarization` 当前继续复用 `AudioTranscriptionReq`
  - `speaker/embedding` 当前继续支持 `url` 与 `base64` 两类音频输入来源
- **usage / 计费口径**
  - `speaker/embedding` 当前继续按响应中的音频时长换算 usage，计量单位继续是毫秒级时长
  - `speaker/diarization` 当前继续至少保留两个 usage 维度：音频时长秒数、识别出的说话人数
  - 两类 speaker 能力在错误场景下，当前继续把这些 usage 维度回落为 `0`

## 4. 回归验证基线

- **TTS / Speech 基线**
  - JSON 入参、音频字节输出、`response_format` 与 `Content-Type` 映射保持一致
  - `stream=true` 时仍输出原始字节流，不出现 SSE / JSON chunk
- **Realtime ASR 基线**
  - WebSocket 建连、`StartTranscription`、音频帧、`StopTranscription`、协议内错误帧与关闭语义保持一致
  - 会话时长 usage 与异常收口规则保持一致
- **OpenAI Transcriptions / Flash ASR 基线**
  - multipart 转 transcription、header+binary 转 flash ASR 的协议边界保持一致
  - Flash ASR 到 OpenAI transcription 的输出转换保持一致
- **Async / Speaker 基线**
  - 异步提交与结果查询结构保持一致
  - embedding / diarization 输出字段、usage 维度与错误回落规则保持一致

## 5. 验证方式

- **契约样例回放**
  - 基于 Java 基线环境分别回放 TTS、Realtime、Transcriptions、Flash ASR、Async、Speaker 的最小请求与异常请求
- **流式与连接级验证**
  - 对字节流与 WebSocket 场景重点比对事件顺序、错误表达、终止方式、连接关闭语义
- **黄金样本比对**
  - 为关键成功响应、错误响应、异步查询结果建立黄金样本，允许 ID/时间类字段存在可控差异
- **治理与收口验证**
  - 通过 requestId / processData、usage、并发计数、日志与成本记录验证收口行为未漂移

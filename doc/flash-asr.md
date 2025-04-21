# 一句话语音识别接口文档

## 目录

- [接口描述](#接口描述)
- [请求](#请求)
  - [HTTP 请求](#http-请求)
  - [请求头](#请求头)
  - [请求体](#请求体)
- [响应](#响应)
  - [响应参数](#响应参数)
- [错误码](#错误码)
- [示例](#示例)
  - [请求示例](#请求示例)
  - [响应示例](#响应示例)
- [最佳实践](#最佳实践)

## 接口描述

一句话语音识别（Flash ASR）接口用于快速识别短音频内容，将语音转换为文本。该接口适用于短语音识别场景，如语音指令、短语音消息等。

## 请求

### HTTP 请求

```http
POST /v1/audio/asr/flash
```

### 请求头

| 参数 | 类型 | 必填 | 默认值 | 描述 |
| --- | --- | --- | --- | --- |
| format | string | 否 | wav | 音频格式，支持 wav、mp3、pcm 等常见格式 |
| sample_rate | integer | 否 | 16000 | 音频采样率，单位为 Hz |
| max_sentence_silence | integer | 否 | 3000 | 最大句子间隔静音时长，单位为毫秒 |
| model | string | 否 | - | 要使用的 ASR 模型，不指定时使用默认模型 |

### 请求体

请求体为二进制音频数据流，直接将音频文件内容作为请求体发送。

支持的音频格式：
- WAV
- MP3
- PCM
- 其他常见音频格式（具体支持情况取决于所选模型）

## 响应

```json
{
  "task_id": "asr-task-123456",
  "user": "user-123",
  "flash_result": {
    "duration": 5600,
    "sentences": [
      {
        "text": "今天天气真不错",
        "begin_time": 0,
        "end_time": 2500
      },
      {
        "text": "我很开心",
        "begin_time": 3000,
        "end_time": 5600
      }
    ]
  }
}
```

### 响应参数

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| task_id | string | 任务 ID，可用于追踪识别任务 |
| user | string | 用户标识 |
| flash_result | object | 识别结果对象 |

#### FlashResult 对象

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| duration | integer | 音频总时长，单位为毫秒 |
| sentences | array | 识别出的句子数组 |

#### Sentence 对象

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| text | string | 识别出的文本内容 |
| begin_time | integer | 句子开始时间，单位为毫秒 |
| end_time | integer | 句子结束时间，单位为毫秒 |

## 错误码

| 错误码 | 描述 |
| --- | --- |
| 400 | 请求参数错误，例如音频格式不支持或参数格式不正确 |
| 401 | 认证失败，无效的 API 密钥 |
| 403 | 权限不足，API 密钥没有权限访问请求的资源 |
| 404 | 请求的资源不存在，例如指定的模型不存在 |
| 413 | 请求实体过大，音频文件超过大小限制 |
| 415 | 不支持的媒体类型，音频格式不受支持 |
| 429 | 请求过多，超出速率限制 |
| 500 | 服务器内部错误 |
| 503 | 服务暂时不可用 |

## 示例

### 请求示例

使用 curl 发送请求：

```bash
curl -X POST "https://api.example.com/v1/audio/asr/flash" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "format: wav" \
  -H "sample_rate: 16000" \
  -H "max_sentence_silence: 2000" \
  -H "model: asr-model-1" \
  --data-binary @audio_file.wav
```

使用 Python 发送请求：

```python
import requests

url = "https://api.example.com/v1/audio/asr/flash"
headers = {
    "Authorization": "Bearer YOUR_API_KEY",
    "format": "wav",
    "sample_rate": "16000",
    "max_sentence_silence": "2000",
    "model": "asr-model-1"
}

with open("audio_file.wav", "rb") as audio_file:
    audio_data = audio_file.read()

response = requests.post(url, headers=headers, data=audio_data)
print(response.json())
```

### 响应示例

```json
{
  "task_id": "asr-task-123456",
  "user": "user-123",
  "flash_result": {
    "duration": 5600,
    "sentences": [
      {
        "text": "今天天气真不错",
        "begin_time": 0,
        "end_time": 2500
      },
      {
        "text": "我很开心",
        "begin_time": 3000,
        "end_time": 5600
      }
    ]
  }
}
```

## 最佳实践

1. **音频质量**：
   - 确保音频清晰，背景噪音小
   - 使用适当的采样率（通常 16kHz 或更高）
   - 避免音频失真或过度压缩

2. **音频长度**：
   - 该接口适合短音频（通常不超过 1 分钟）
   - 对于较长音频，建议使用文件转写接口

3. **静音处理**：
   - 根据实际需求调整 `max_sentence_silence` 参数
   - 较小的值可以更敏感地检测句子边界
   - 较大的值适合语速较慢或有自然停顿的语音

4. **格式选择**：
   - WAV 格式通常提供最好的识别效果
   - 对于网络传输，可以考虑使用 MP3 等压缩格式，但可能会轻微影响识别准确性
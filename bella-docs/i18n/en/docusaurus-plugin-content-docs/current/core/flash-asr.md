# Single-sentence Voice Recognition Interface Documentation

## Table of Contents

- [Interface Description](#interface-description)
- [Request](#request)
  - [HTTP Request](#http-request)
  - [Request Headers](#request-headers)
  - [Request Body](#request-body)
- [Response](#response)
  - [Response Parameters](#response-parameters)
- [Error Codes](#error-codes)
- [Examples](#examples)
  - [Request Example](#request-example)
  - [Response Example](#response-example)
- [Best Practices](#best-practices)

## Interface Description

The Single-sentence Voice Recognition (Flash ASR) interface is used for quick recognition of short audio content, converting speech to text. This interface is suitable for short speech recognition scenarios, such as voice commands, short voice messages, etc.

## Request

### HTTP Request

```http
POST /v1/audio/asr/flash
```

### Request Headers

| Parameter | Type | Required | Default Value | Description |
| --- | --- | --- | --- | --- |
| format | string | No | wav | Audio format, supports wav, mp3, pcm and other common formats |
| sample_rate | integer | No | 16000 | Audio sampling rate, in Hz |
| max_sentence_silence | integer | No | 3000 | Maximum silence duration between sentences, in milliseconds |
| model | string | No | - | ASR model to use, uses the default model if not specified |

### Request Body

The request body is a binary audio data stream, with the audio file content sent directly as the request body.

Supported audio formats:
- WAV
- MP3
- PCM
- Other common audio formats (specific support depends on the selected model)

## Response

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

### Response Parameters

| Parameter | Type | Description |
| --- | --- | --- |
| task_id | string | Task ID, can be used to track the recognition task |
| user | string | User identifier |
| flash_result | object | Recognition result object |

#### FlashResult Object

| Parameter | Type | Description |
| --- | --- | --- |
| duration | integer | Total audio duration, in milliseconds |
| sentences | array | Array of recognized sentences |

#### Sentence Object

| Parameter | Type | Description |
| --- | --- | --- |
| text | string | Recognized text content |
| begin_time | integer | Sentence start time, in milliseconds |
| end_time | integer | Sentence end time, in milliseconds |

## Error Codes

| Error Code | Description |
| --- | --- |
| 400 | Request parameter error, such as unsupported audio format or incorrect parameter format |
| 401 | Authentication failed, invalid API key |
| 403 | Insufficient permissions, API key doesn't have permission to access the requested resource |
| 404 | Requested resource does not exist, such as the specified model does not exist |
| 413 | Request entity too large, audio file exceeds size limit |
| 415 | Unsupported media type, audio format not supported |
| 429 | Too many requests, exceeded rate limit |
| 500 | Internal server error |
| 503 | Service temporarily unavailable |

## Examples

### Request Example

Using curl to send a request:

```bash
curl -X POST "https://api.example.com/v1/audio/asr/flash" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "format: wav" \
  -H "sample_rate: 16000" \
  -H "max_sentence_silence: 2000" \
  -H "model: asr-model-1" \
  --data-binary @audio_file.wav
```

Using Python to send a request:

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

### Response Example

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

## Best Practices

1. **Audio Quality**:
   - Ensure audio is clear with minimal background noise
   - Use appropriate sampling rates (typically 16kHz or higher)
   - Avoid audio distortion or excessive compression

2. **Audio Length**:
   - This interface is suitable for short audio (typically not exceeding 1 minute)
   - For longer audio, it's recommended to use the file transcription interface

3. **Silence Handling**:
   - Adjust the `max_sentence_silence` parameter according to actual needs
   - Smaller values can more sensitively detect sentence boundaries
   - Larger values are suitable for speech with slower pace or natural pauses

4. **Format Selection**:
   - WAV format typically provides the best recognition results
   - For network transmission, compressed formats like MP3 can be considered, but may slightly affect recognition accuracy
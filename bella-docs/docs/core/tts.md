# 语音合成接口文档

## 目录

- [接口描述](#接口描述)
- [请求](#请求)
  - [HTTP 请求](#http-请求)
  - [请求体](#请求体)
- [响应](#响应)
- [错误码](#错误码)
- [示例](#示例)
  - [请求示例](#请求示例)
  - [流式响应](#流式响应)

## 接口描述

根据输入的文本生成语音。支持多种声音和格式，并且支持流式响应，适用于实时语音合成场景。

## 请求

### HTTP 请求

```http
POST /v1/audio/speech
```

### 请求体

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | --- | --- |
| input | string | 是 | 要生成语音的文本。最大长度为 4096 个字符 |
| model | string | 是 | 要使用的 TTS 模型，例如：tts-1, tts-1-hd 或 gpt-4o-mini-tts |
| voice | string | 是 | 生成语音时使用的声音。支持的声音根据模型确定。
| response_format | string | 否 | 音频格式。支持的格式有 mp3, opus, aac, flac, wav 和 pcm。默认值根据模型类型确定 |
| speed | number | 否 | 生成的音频速度。可选值范围从 0.25 到 4.0。默认为 1.0 |
| stream | boolean | 否 | 是否启用流式响应。默认为 true |
| sample_rate | integer | 否 | 音频采样率。默认根据模型和格式自动选择最佳值 |
| user | string | 否 | 表示最终用户的唯一标识符 |

#### 声音选项

以下是可用的声音选项及其特点：

- **alloy**: 中性、平衡的声音
- **ash**: 年轻、清晰的声音
- **ballad**: 柔和、平静的声音
- **coral**: 温暖、友好的声音
- **echo**: 深沉、有力的声音
- **fable**: 权威、自信的声音
- **onyx**: 深沉、庄重的声音
- **nova**: 活泼、热情的声音
- **sage**: 平静、沉稳的声音
- **shimmer**: 明亮、欢快的声音
- **verse**: 抒情、富有表现力的声音

#### 响应格式选项

以下是支持的音频格式及其特点：

- **mp3**: 高压缩率，适合网络传输，默认选项
- **opus**: 低延迟，适合实时应用
- **aac**: 高质量，适合音乐
- **flac**: 无损压缩，适合高质量需求
- **wav**: 无压缩，适合高质量需求
- **pcm**: 原始音频数据

## 响应

接口返回音频文件内容。

- 当 `stream=false` 时，返回完整的音频文件
- 当 `stream=true` 时，以流的形式返回音频数据，客户端可以边接收边播放

响应的 Content-Type 根据请求的 `response_format` 参数设置：

| response_format | Content-Type |
| --- | --- |
| mp3 | audio/mpeg |
| opus | audio/opus |
| aac | audio/aac |
| flac | audio/flac |
| wav | audio/wav |
| pcm | audio/pcm |

## 错误码

| 错误码 | 描述 |
| --- | --- |
| 400 | 请求参数错误，例如文本过长或参数格式不正确 |
| 401 | 认证失败，无效的 API 密钥 |
| 403 | 权限不足，API 密钥没有权限访问请求的资源 |
| 404 | 请求的资源不存在，例如指定的模型不存在 |
| 429 | 请求过多，超出速率限制 |
| 500 | 服务器内部错误 |
| 503 | 服务暂时不可用 |

## 示例

### 请求示例

#### 基本请求

```json
{
  "input": "今天天气真不错，阳光明媚，让人心情愉悦。",
  "model": "tts-1",
  "voice": "alloy",
  "response_format": "mp3",
  "speed": 1.0
}
```

#### 流式响应请求

```json
{
  "input": "今天天气真不错，阳光明媚，让人心情愉悦。",
  "model": "tts-1",
  "voice": "nova",
  "response_format": "mp3",
  "speed": 1.0,
  "stream": true
}
```

#### 高质量语音请求

```json
{
  "input": "今天天气真不错，阳光明媚，让人心情愉悦。",
  "model": "tts-1-hd",
  "voice": "shimmer",
  "response_format": "wav",
  "speed": 0.9,
  "sample_rate": 24000
}
```

### 流式响应

当 `stream=true` 时，服务器会以流的形式返回音频数据。客户端可以边接收边播放，适用于实时语音合成场景，如在线客服、导航系统等。

流式响应的优势：

1. **低延迟**：用户无需等待整个音频生成完成，可以立即听到开始部分
2. **实时体验**：适用于需要即时反馈的交互场景
3. **资源效率**：客户端可以在接收的同时处理音频，减少内存占用

## 实现示例

### JavaScript 客户端示例

```javascript
async function streamSpeech() {
  const response = await fetch('https://api.example.com/v1/audio/speech', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer YOUR_API_KEY'
    },
    body: JSON.stringify({
      input: "今天天气真不错，阳光明媚，让人心情愉悦。",
      model: "tts-1",
      voice: "nova",
      response_format: "mp3",
      stream: true
    })
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  // 创建音频上下文
  const audioContext = new (window.AudioContext || window.webkitAudioContext)();
  const reader = response.body.getReader();
  
  // 处理音频流
  const processStream = async () => {
    const { done, value } = await reader.read();
    if (done) return;
    
    // 解码音频数据并播放
    audioContext.decodeAudioData(value.buffer, (buffer) => {
      const source = audioContext.createBufferSource();
      source.buffer = buffer;
      source.connect(audioContext.destination);
      source.start(0);
    });
    
    // 继续处理流
    processStream();
  };
  
  processStream();
}
```

### Python 客户端示例

```python
import requests
import pyaudio
import io
from pydub import AudioSegment

def stream_speech():
    url = "https://api.example.com/v1/audio/speech"
    headers = {
        "Content-Type": "application/json",
        "Authorization": "Bearer YOUR_API_KEY"
    }
    data = {
        "input": "今天天气真不错，阳光明媚，让人心情愉悦。",
        "model": "tts-1",
        "voice": "nova",
        "response_format": "mp3",
        "stream": True
    }
    
    # 发送请求并获取流式响应
    response = requests.post(url, json=data, headers=headers, stream=True)
    
    if response.status_code != 200:
        raise Exception(f"Error: {response.status_code}")
    
    # 初始化PyAudio
    p = pyaudio.PyAudio()
    stream = p.open(format=pyaudio.paInt16,
                    channels=1,
                    rate=24000,
                    output=True)
    
    # 处理音频流
    buffer = io.BytesIO()
    for chunk in response.iter_content(chunk_size=4096):
        if chunk:
            buffer.write(chunk)
            # 当积累足够的数据时，播放
            if buffer.tell() > 8192:
                buffer.seek(0)
                audio = AudioSegment.from_mp3(buffer)
                stream.write(audio.raw_data)
                buffer = io.BytesIO()
    
    # 播放剩余数据
    if buffer.tell() > 0:
        buffer.seek(0)
        audio = AudioSegment.from_mp3(buffer)
        stream.write(audio.raw_data)
    
    # 关闭流
    stream.stop_stream()
    stream.close()
    p.terminate()
```

## 最佳实践

1. **选择合适的声音**：根据应用场景选择合适的声音，例如客服系统可以选择温暖友好的声音（coral），导航系统可以选择清晰权威的声音（fable）
2. **控制文本长度**：较长的文本可能导致生成时间增加，建议将长文本分段处理
3. **调整语速**：根据应用场景调整语速，例如通知类信息可以稍快，解释类信息可以稍慢
4. **选择合适的音频格式**：对于网络传输，mp3 和 opus 格式更为紧凑；对于高质量需求，可以选择 wav 或 flac
5. **利用流式响应**：在需要实时反馈的场景中，启用流式响应可以显著提升用户体验
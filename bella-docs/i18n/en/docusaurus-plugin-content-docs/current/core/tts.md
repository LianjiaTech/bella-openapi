# Speech Synthesis Interface Documentation

## Table of Contents

- [Interface Description](#interface-description)
- [Request](#request)
  - [HTTP Request](#http-request)
  - [Request Body](#request-body)
- [Response](#response)
- [Error Codes](#error-codes)
- [Examples](#examples)
  - [Request Examples](#request-examples)
  - [Streaming Response](#streaming-response)

## Interface Description

Generates speech from input text. Supports multiple voices and formats, and offers streaming responses suitable for real-time speech synthesis scenarios.

## Request

### HTTP Request

```http
POST /v1/audio/speech
```

### Request Body

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| input | string | Yes | The text to generate speech from. Maximum length is 4096 characters |
| model | string | Yes | The TTS model to use, e.g.: tts-1, tts-1-hd, or gpt-4o-mini-tts |
| voice | string | Yes | The voice to use for speech generation. Supported voices depend on the model |
| response_format | string | No | Audio format. Supported formats include mp3, opus, aac, flac, wav, and pcm. Default value depends on the model type |
| speed | number | No | The speed of the generated audio. Optional values range from 0.25 to 4.0. Default is 1.0 |
| stream | boolean | No | Whether to enable streaming response. Default is true |
| sample_rate | integer | No | Audio sampling rate. Default is automatically selected based on model and format |
| user | string | No | A unique identifier representing the end user |

#### Voice Options

Here are the available voice options and their characteristics:

- **alloy**: Neutral, balanced voice
- **ash**: Young, clear voice
- **ballad**: Soft, calm voice
- **coral**: Warm, friendly voice
- **echo**: Deep, powerful voice
- **fable**: Authoritative, confident voice
- **onyx**: Deep, solemn voice
- **nova**: Lively, enthusiastic voice
- **sage**: Calm, steady voice
- **shimmer**: Bright, cheerful voice
- **verse**: Lyrical, expressive voice

#### Response Format Options

Here are the supported audio formats and their characteristics:

- **mp3**: High compression ratio, suitable for network transmission, default option
- **opus**: Low latency, suitable for real-time applications
- **aac**: High quality, suitable for music
- **flac**: Lossless compression, suitable for high-quality requirements
- **wav**: Uncompressed, suitable for high-quality requirements
- **pcm**: Raw audio data

## Response

The interface returns audio file content.

- When `stream=false`, the complete audio file is returned
- When `stream=true`, audio data is returned as a stream, allowing clients to play while receiving

The Content-Type of the response is set according to the `response_format` parameter in the request:

| response_format | Content-Type |
| --- | --- |
| mp3 | audio/mpeg |
| opus | audio/opus |
| aac | audio/aac |
| flac | audio/flac |
| wav | audio/wav |
| pcm | audio/pcm |

## Error Codes

| Error Code | Description |
| --- | --- |
| 400 | Request parameter error, such as text too long or incorrect parameter format |
| 401 | Authentication failed, invalid API key |
| 403 | Insufficient permissions, API key doesn't have permission to access the requested resource |
| 404 | Requested resource doesn't exist, such as the specified model doesn't exist |
| 429 | Too many requests, exceeded rate limit |
| 500 | Internal server error |
| 503 | Service temporarily unavailable |

## Examples

### Request Examples

#### Basic Request

```json
{
  "input": "The weather today is really nice, sunny, making people feel happy.",
  "model": "tts-1",
  "voice": "alloy",
  "response_format": "mp3",
  "speed": 1.0
}
```

#### Streaming Response Request

```json
{
  "input": "The weather today is really nice, sunny, making people feel happy.",
  "model": "tts-1",
  "voice": "nova",
  "response_format": "mp3",
  "speed": 1.0,
  "stream": true
}
```

#### High-Quality Speech Request

```json
{
  "input": "The weather today is really nice, sunny, making people feel happy.",
  "model": "tts-1-hd",
  "voice": "shimmer",
  "response_format": "wav",
  "speed": 0.9,
  "sample_rate": 24000
}
```

### Streaming Response

When `stream=true`, the server returns audio data as a stream. Clients can play while receiving, suitable for real-time speech synthesis scenarios such as online customer service, navigation systems, etc.

Advantages of streaming responses:

1. **Low latency**: Users don't need to wait for the entire audio generation to complete; they can immediately hear the beginning
2. **Real-time experience**: Suitable for interactive scenarios requiring immediate feedback
3. **Resource efficiency**: Clients can process audio while receiving, reducing memory usage

## Implementation Examples

### JavaScript Client Example

```javascript
async function streamSpeech() {
  const response = await fetch('https://api.example.com/v1/audio/speech', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer YOUR_API_KEY'
    },
    body: JSON.stringify({
      input: "The weather today is really nice, sunny, making people feel happy.",
      model: "tts-1",
      voice: "nova",
      response_format: "mp3",
      stream: true
    })
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  // Create audio context
  const audioContext = new (window.AudioContext || window.webkitAudioContext)();
  const reader = response.body.getReader();
  
  // Process audio stream
  const processStream = async () => {
    const { done, value } = await reader.read();
    if (done) return;
    
    // Decode and play audio data
    audioContext.decodeAudioData(value.buffer, (buffer) => {
      const source = audioContext.createBufferSource();
      source.buffer = buffer;
      source.connect(audioContext.destination);
      source.start(0);
    });
    
    // Continue processing the stream
    processStream();
  };
  
  processStream();
}
```

### Python Client Example

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
        "input": "The weather today is really nice, sunny, making people feel happy.",
        "model": "tts-1",
        "voice": "nova",
        "response_format": "mp3",
        "stream": True
    }
    
    # Send request and get streaming response
    response = requests.post(url, json=data, headers=headers, stream=True)
    
    if response.status_code != 200:
        raise Exception(f"Error: {response.status_code}")
    
    # Initialize PyAudio
    p = pyaudio.PyAudio()
    stream = p.open(format=pyaudio.paInt16,
                    channels=1,
                    rate=24000,
                    output=True)
    
    # Process audio stream
    buffer = io.BytesIO()
    for chunk in response.iter_content(chunk_size=4096):
        if chunk:
            buffer.write(chunk)
            # Play when enough data accumulates
            if buffer.tell() > 8192:
                buffer.seek(0)
                audio = AudioSegment.from_mp3(buffer)
                stream.write(audio.raw_data)
                buffer = io.BytesIO()
    
    # Play remaining data
    if buffer.tell() > 0:
        buffer.seek(0)
        audio = AudioSegment.from_mp3(buffer)
        stream.write(audio.raw_data)
    
    # Close stream
    stream.stop_stream()
    stream.close()
    p.terminate()
```

## Best Practices

1. **Choose appropriate voices**: Select suitable voices based on application scenarios; for example, customer service systems can choose warm, friendly voices (coral), while navigation systems can choose clear, authoritative voices (fable)
2. **Control text length**: Longer texts may increase generation time; it is recommended to process long texts in segments
3. **Adjust speech speed**: Adjust speed according to the application scenario; for example, notifications can be slightly faster, explanations can be slightly slower
4. **Choose appropriate audio formats**: For network transmission, mp3 and opus formats are more compact; for high-quality requirements, wav or flac can be chosen
5. **Utilize streaming responses**: In scenarios requiring real-time feedback, enabling streaming responses can significantly enhance user experience
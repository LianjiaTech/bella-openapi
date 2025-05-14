# Bella OpenAPI Real-time Voice Dialogue Interface Documentation

## Table of Contents

- [1. Overview](#1-overview)
  - [1.1 Features](#11-features)
  - [1.2 Application Scenarios](#12-application-scenarios)
- [2. Interface Specifications](#2-interface-specifications)
  - [2.1 Basic Information](#21-basic-information)
  - [2.2 Request Parameters](#22-request-parameters)
    - [2.2.1 URL Parameters](#221-url-parameters)
    - [2.2.2 WebSocket Startup Messages](#222-websocket-startup-messages)
    - [2.2.3 Audio Data Format](#223-audio-data-format)
  - [2.3 Response Message Types](#23-response-message-types)
    - [2.3.1 Transcription-related Messages](#231-transcription-related-messages)
    - [2.3.2 LLM-related Messages](#232-llm-related-messages)
    - [2.3.3 Speech Synthesis-related Messages](#233-speech-synthesis-related-messages)
    - [2.3.4 VAD-related Messages](#234-vad-related-messages)
    - [2.3.5 Session Control and Error Messages](#235-session-control-and-error-messages)
- [3. Implementation Guide](#3-implementation-guide)
  - [3.1 Java Implementation Example](#31-java-implementation-example)
  - [3.2 JavaScript Implementation Example](#32-javascript-implementation-example)
  - [3.3 Available Speech Synthesis Voices](#33-available-speech-synthesis-voices)
- [4. Error Handling](#4-error-handling)
- [5. Best Practices](#5-best-practices)

## 1. Overview

The Real-time Voice Dialogue Interface provides an all-in-one solution for speech recognition, large language model conversation, and speech synthesis. It supports users inputting speech through a microphone in real-time, with the system automatically recognizing speech content, calling a large language model to generate responses, and converting the response content into speech output. The entire process is implemented through WebSocket protocol, featuring low latency and high real-time performance.

### 1.1 Features

- **Real-time Speech Recognition**: Supports real-time speech input from users and converts speech to text
- **Streaming Large Language Model Dialogue**: Sends recognized text to a large language model and receives streaming responses
- **Speech Synthesis**: Converts large language model responses into speech, providing a natural voice dialogue experience
- **Full-duplex Communication**: Based on WebSocket protocol, supports full-duplex communication for true real-time dialogue

### 1.2 Application Scenarios

- Intelligent voice assistants
- Customer service chatbots
- Voice interaction systems
- Accessibility applications
- In-vehicle voice systems

## 2. Interface Specifications

### 2.1 Basic Information

- **Interface Path**: `/v1/audio/realtime` or `/v1/audio/asr/stream` (returns only transcription events)
- **Protocol**: WebSocket
- **Request Method**: GET
- **Authentication Method**: Bearer Token (passed through the `Authorization` request header)

### 2.2 Request Parameters

#### 2.2.1 URL Parameters

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| model | string | No | Specifies the model to use; if not specified, the default model for the capability will be used |

#### 2.2.2 WebSocket Startup Messages

After establishing a connection, the client needs to send a startup message in JSON format:
For `/v1/audio/realtime`, the startup message is as follows:
For `/v1/audio/asr/stream`, the `llm_option` and `tts_option` parameters are not required

##### StartTranscription Message

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
        "sys_prompt": "You are a versatile voice assistant. Your responses will be converted to audio for the user, so please respond as concisely as possible, and end your first sentence quickly to better facilitate streaming speech synthesis."
      }
    },
    "tts_option": {
      "model": "chat-tts",
      "sample_rate": 24000
    }
  }
}
```

Parameter Description:

- **header**: Message header information
  - **message_id**: Unique message identifier, UUID recommended
  - **task_id**: Unique task identifier, UUID recommended
  - **namespace**: Fixed as "SpeechTranscriber"
  - **name**: Fixed as "StartTranscription"
  - **appkey**: Fixed as "default"

- **payload**: Task configuration parameters
  - **format**: Audio format, fixed as "pcm"
  - **sample_rate**: Audio sampling rate, 16000 recommended
  - **enable_intermediate_result**: Whether to enable intermediate results, true recommended
  - **enable_punctuation_prediction**: Whether to enable punctuation prediction, true recommended
  - **enable_inverse_text_normalization**: Whether to enable inverse text normalization, true recommended
  - **llm_option**: Large language model options (optional)
    - **main**: Main model configuration
      - **model**: Model name, e.g., "qwen2.5-coder:3b"
      - **sys_prompt**: System prompt, used to set the model's behavior and role, can use Python Jinja2 templates
      - **prompt**: User prompt, can provide Python Jinja2 templates to rewrite user messages sent to the large model through context
      - **temperature**: Temperature parameter, controls the randomness of output, higher values increase randomness, range [0.0, 2.0], default is 1.0
    - **workers**: Worker model configuration list (optional), each worker model includes the following properties
      - **model**: Model name
      - **blocking**: Whether to block, default is false
      - **variable_name**: Result variable name
      - **variable_type**: Variable type
      - **sys_prompt**: System prompt, can use Python Jinja2 templates
      - **prompt**: User prompt, can use Python Jinja2 templates
      - **temperature**: Temperature parameter, default is 1.0
      - **json_schema**: JSON schema definition
  - **tts_option**: Speech synthesis options (optional)
    - **model**: Speech synthesis model, e.g., "chat-tts"
    - **sample_rate**: Synthesis audio sampling rate, 24000 recommended
    - **voice**: Synthesis voice (optional), available voices depend on the selected model
  - **variables**: Variable configuration (optional) map, used with prompt templates

##### Configuration Example

Below is a complete configuration example including main and worker models, showing how to use Jinja2 templates and variables for message rewriting:

```json
{
  "llm_option": {
    "main": {
      "model": "qwen2.5-coder:3b",
      "sys_prompt": "You are a versatile voice assistant. Your responses will be converted to audio for the user, so please respond as concisely as possible, and end your first sentence quickly to better facilitate streaming speech synthesis."
    },
    "workers": [
      {
        "model": "qwen2.5-coder:3b",
        "blocking": true,
        "variable_name": "rewrite_user_message",
        "sys_prompt": "You are a user message rewrite expert, responsible for rewriting user messages transcribed from voice to make them more fluent and coherent, while also completing necessary reference disambiguation. Don't explain, just rewrite. If no rewriting is needed, output the user's original content directly.",
        "prompt": "## User Historical Dialogue\n<history_messages>\n{% for message in history_messages %}\n{% if message.role == \"user\" %}\n  <user timestamp=\"{{loop.index}}\">{{{ message.content }}}</user>\n{% elif message.role == \"assistant\" %}\n  <agent timestamp=\"{{loop.index}}\">{{{ message.content }}}</agent>\n{% endif %}\n{% endfor %}\n\n</history_messages>\n\n## User Latest Message\n<user_message>\n  {{user_message}}\n</user_message>\n\nPlease rewrite the message now!\n"
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

This configuration example shows:
1. Main model configuration: Using qwen2.5-coder:3b model for dialogue with users
2. Worker model configuration: Using the same model as a worker, responsible for rewriting user messages
3. Worker model using Jinja2 templates to process historical messages and current user messages
4. TTS configuration: Specifying model, voice, and sampling rate

##### StopTranscription Message

When you need to end a transcription task, the client needs to send a StopTranscription message:
After the server processes unfinished messages, it will return a SessionClose message.

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

#### 2.2.3 Audio Data

After sending the startup message, the client needs to continuously send audio data in binary format. Audio data requirements:

- Format: PCM (Pulse Code Modulation)
- Sampling rate: 16000Hz
- Bit depth: 16-bit
- Channels: Single channel (mono)
- Frame size: 3200 bytes recommended (corresponding to 100ms of audio)

### 2.3 Response Messages

The server will send various types of text messages and binary data through the WebSocket connection. Text messages are in JSON format and mainly include the following types:

#### 2.3.1 Transcription-related Messages

- **TranscriptionStarted**: Transcription task started
- **SentenceBegin**: Speech beginning detected
- **TranscriptionResultChanged**: Transcription result updated
- **SentenceEnd**: Speech sentence ended, includes final transcription result
- **TranscriptionCompleted**: Transcription task completed
- **TranscriptionFailed**: Transcription task failed

Example:
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
    "result": "Hello, I'd like to know what today's weather is like"
  }
}
```

#### 2.3.2 LLM-related Messages

- **LLM_CHAT_BEGIN**: Large model starts generating response
- **LLM_CHAT_DELTA**: Large model response content update
- **LLM_CHAT_END**: Large model response ended
- **LLM_CHAT_CANCELLED**: Large model response cancelled

Example:
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
    "data": "Hello! Today in Beijing, the weather is clear, with temperatures ranging from 22°C to 28°C, good air quality, and it's a great day for outdoor activities."
  }
}
```

#### 2.3.3 Speech Synthesis-related Messages

- **TTS_BEGIN**: Speech synthesis started
- **TTS_TTFT**: First audio data package ready (Time To First Token)
- **TTS_DELTA**: Speech synthesis incremental data
- **TTS_END**: Speech synthesis ended

Speech synthesis audio data is sent through binary messages, in PCM format, with a sampling rate of 24000Hz, 16-bit, single channel.

#### 2.3.4 VAD-related Messages (Voice Activity Detection)

- **VOICE_QUIET**: Silence detected
- **VOICE_STARTING**: Start of speech detected
- **VOICE_SPEAKING**: Speaking detected
- **VOICE_STOPPING**: Stop speaking detected
- **VOICE_PAUSING**: Speech pause detected

Example:
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

#### 2.3.5 Session Control and Error Messages

- **SESSION_CLOSE**: Session closed, after the client sends a StopTranscription message, the server will return a SessionClose message after processing unfinished messages.
- **TASK_FAILED**: Task failed
- **LLM_CHAT_ERROR**: LLM chat error
- **LLM_WORKER_ERROR**: LLM subtask error

Example:
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

## 3. Implementation Guide

### 3.1 Java Implementation Example

For Java implementation of the real-time voice interface, refer to the [RealtimeDemo.java](https://github.com/LianjiaTech/bella-openapi/blob/develop/api/server/src/test/java/com/ke/bella/openapi/RealtimeDemo.java) class in the project.

This example demonstrates how to use the OkHttp library to implement WebSocket connections and complete the following functions:

1. Establish a WebSocket connection and send a StartTranscription message
2. Send audio data (PCM format)
3. Process various event types (transcription, VAD, LLM, TTS, etc.)
4. Receive and process TTS audio data
5. Send a StopTranscription message to end the session

The example includes complete error handling and resource release logic, which can serve as an implementation reference.

### 3.2 JavaScript Implementation Example

For JavaScript implementation of the real-time voice interface, refer to the [RealtimeAudioRecorder.ts](https://github.com/LianjiaTech/bella-openapi/blob/develop/web/src/components/playground/RealtimeAudioRecorder.ts) class in the project.

### 3.3 Available Speech Synthesis Voices
Different models have different available voices. For details, please visit the bella-openapi homepage.

## 4. Error Handling

### 4.1 Common Error Codes

| Error Code | Description | Solution |
| --- | --- | --- |
| 401 | Unauthorized | Check if the API Key is correct |
| 403 | Forbidden | Check API Key permissions |
| 429 | Too Many Requests | Reduce request frequency |
| 500 | Internal Server Error | Contact technical support |

### 4.2 WebSocket Connection Errors

| Error Type | Possible Cause | Solution |
| --- | --- | --- |
| Connection Failed | Network issues or service unavailable | Check network connection, try again later |
| Connection Closed | Server closed connection or network interruption | Implement automatic reconnection mechanism |
| Message Parsing Error | Invalid JSON format | Check message format |

## 5. Best Practices

### 5.1 Audio Quality Optimization

- Use high-quality microphones, reduce environmental noise
- Maintain appropriate volume and distance
- Use noise reduction and echo cancellation technology
- Ensure audio sampling rate and format meet requirements

### 5.2 Performance Optimization

- Implement connection recovery mechanism
- Optimize audio data processing and sending logic
- Use WebWorker to process audio data, avoid blocking the main thread
- Implement buffering mechanism to smooth network fluctuations

### 5.3 User Experience Optimization

- Provide clear recording status indication
- Display real-time transcription results, provide visual feedback
- Implement voice activity detection, automatically start and end recording
- Provide volume visualization to help users adjust volume

## 6. Example Code

For complete frontend implementation examples, refer to the following files in the Bella OpenAPI project:

- `web/src/app/playground/v1/audio/realtime/page.tsx`: Real-time voice dialogue page
- `web/src/components/playground/RealtimeAudioRecorder.ts`: Real-time voice recorder
- `web/src/components/playground/PCMPlayer.ts`: PCM audio player

## 7. Frequently Asked Questions

### Q1: Why is my speech recognition result inaccurate?
A1: This may be due to microphone quality, environmental noise, or network issues. It is recommended to use a high-quality microphone, use it in a quiet environment, and ensure stable network connection.

### Q2: How to handle compatibility issues with different browsers?
A2: Use the WebRTC adapter library to handle compatibility issues across different browsers, and provide friendly prompts on unsupported browsers.

### Q3: How to optimize performance on mobile devices?
A3: Reduce audio buffer size, optimize audio processing logic, use lower sampling rates, and implement battery optimization strategies.

### Q4: How to handle unstable network conditions?
A4: Implement connection recovery mechanism, use audio buffering strategies, and resynchronize dialogue state after network recovery.

### Q5: Which languages does the system support?
A5: Supported languages depend on the selected model. Please refer to the model documentation for the specific list of supported languages.
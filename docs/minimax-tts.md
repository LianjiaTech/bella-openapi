# MiniMax TTS Mapping

## Scope

- Bella endpoint: `/v1/audio/speech`
- MiniMax upstream: `/v1/t2a_v2`
- Channel protocol: `MiniMaxAdaptor`
- Spring bean name: `MiniMaxTts`
- Channel info fields: `auth`, `deployName`, `groupId`, `defaultVoice`, `defaultContentType`, `defaultSampleRate`

MiniMax is currently integrated as a synchronous HTTP TTS adaptor. Bella does not expose `/v1/t2a_v2`; requests still enter through the OpenAI-compatible `/v1/audio/speech` endpoint.

## Field Mapping

| Bella `TtsRequest` | MiniMax HTTP T2A |
| --- | --- |
| `input` | `text` |
| `model` or channel `deployName` | `model` |
| `voice` or channel `defaultVoice` | `voice_setting.voice_id` |
| `speed` | `voice_setting.speed` |
| `response_format` or channel `defaultContentType` | `audio_setting.format` |
| `sample_rate` or channel `defaultSampleRate` | `audio_setting.sample_rate` |
| `extra_body.voice_setting` | MiniMax `voice_setting` extension fields |
| `extra_body.audio_setting` | MiniMax `audio_setting` extension fields |
| other `extra_body` fields | MiniMax root extension fields |
| MiniMax `data.audio` hex | Bella audio bytes |

Bella standard fields take precedence over conflicting reserved MiniMax fields: `text`, `model`, `stream`, `output_format`, `voice_setting.voice_id`, `voice_setting.speed`, `audio_setting.format`, and `audio_setting.sample_rate`.

The adaptor always sends MiniMax `stream=false` and `output_format=hex`, then decodes the returned JSON `data.audio` hex payload into the bytes returned by Bella.

## Streaming Note

MiniMax TTS uses the synchronous HTTP API in this adaptor. It does not connect MiniMax's WebSocket streaming API.

When Bella receives `stream=true`, the adaptor reuses the synchronous HTTP result and sends the decoded audio as one callback payload. This is not upstream chunk-by-chunk streaming. If the synchronous MiniMax request fails before audio is available, the exception is propagated so Bella can handle it as an endpoint error instead of returning an empty `200` audio response.

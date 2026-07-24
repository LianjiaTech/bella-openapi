# Realtime TTS Protocol Notes

Realtime TTS uses Bella standard payload fields for common session options:

- `model`
- `voice`
- `format`
- `sample_rate`
- `channels`
- `encoding`
- `speed`
- `volume`
- `pitch`
- `enable_timestamp`

Provider-specific session options are carried by `payload.extra_body` on
`StartSpeech` only. Do not repeat these options on `InputText`.

## Extra Body Semantics

For the realtime TTS WebSocket endpoint, `StartSpeech.payload.extra_body` is
scoped to provider session creation. Bella merges its fields directly into the
provider `req_params` object; it is not merged into the provider request root.

This is intentionally different from the HTTP TTS request shape. Huoshan HTTP
TTS can use an outer `req_params` wrapper:

```json
{
  "extra_body": {
    "req_params": {
      "additions": "{\"disable_markdown_filter\":true}"
    }
  }
}
```

The equivalent realtime TTS `StartSpeech` payload omits that wrapper:

```json
{
  "payload": {
    "extra_body": {
      "additions": {
        "disable_markdown_filter": true
      }
    }
  }
}
```

Do not send `payload.extra_body.req_params` to the realtime endpoint. Common
session settings should continue to use Bella standard fields, while
`extra_body` is reserved for provider-specific session extensions.

## Huoshan Additions

For Huoshan realtime TTS, Bella merges `payload.extra_body` into the provider
`req_params` object when creating the session. Clients should not send the
outer `req_params` wrapper.

The realtime Bella request keeps `extra_body.additions` as a JSON object so it
can be merged with standard options such as `pitch`. The Huoshan adapter then
serializes the merged object into the JSON string required by the provider's
`req_params.additions` field.

Client `StartSpeech` example:

```json
{
  "header": {
    "name": "StartSpeech",
    "message_id": "msg_001",
    "task_id": "task_001"
  },
  "payload": {
    "model": "doubao-seed-tts-2.0",
    "voice": "zh_female_vv_uranus_bigtts",
    "format": "pcm",
    "sample_rate": 24000,
    "channels": 1,
    "encoding": "s16le",
    "extra_body": {
      "additions": {
        "max_length_to_filter_parenthesis": 100,
        "disable_markdown_filter": true
      }
    }
  }
}
```

Huoshan session request payload:

```json
{
  "event": 100,
  "req_params": {
    "speaker": "zh_female_vv_uranus_bigtts",
    "audio_params": {
      "format": "pcm",
      "sample_rate": 24000,
      "channels": 1,
      "encoding": "s16le"
    },
    "additions": "{\"max_length_to_filter_parenthesis\":100,\"disable_markdown_filter\":true}"
  }
}
```

Reserved Bella-generated fields cannot be overridden through `extra_body`:
`text`, `speaker`, `text_type`, and `audio_params`. The root `additions` field
is handled specially: `extra_body.additions` is deep-merged with
Bella-generated additions. When `payload.pitch` generates
`additions.post_process.pitch`, the standard `payload.pitch` value has priority
over `extra_body.additions.post_process.pitch`.

## Timestamp Metadata

`SpeechTimestamp` responses expose normalized timestamp items in `payload.items`.
Provider raw timestamp data is returned separately in
`payload.provider_metadata`.

```json
{
  "payload": {
    "type": "word",
    "items": [],
    "provider_metadata": {}
  }
}
```

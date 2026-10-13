# Realtime ASR Handshake Tracing

The `/v1/audio/asr/stream` WebSocket endpoint reports upstream handshake
identifiers in the first `TranscriptionStarted` response when the provider
returns them. The identifiers are optional and do not change the connection,
audio, recognition, stop, or close protocol.

## First response

The fields are placed in `payload.additions`:

```json
{
  "header": {"name": "TranscriptionStarted", "task_id": "task-001"},
  "payload": {
    "additions": {
      "logid": "provider-log-123",
      "connect_id": "provider-connect-456"
    }
  }
}
```

`logid` comes from the upstream `X-Tt-Logid` response header. `connect_id`
comes from the `X-Api-Connect-Id` response header, falling back to the ID
sent in the upstream handshake request when the response does not echo it.
Each key is emitted only when its value is
non-blank; older clients can ignore `payload.additions`.

Examples:

```json
{"payload":{"additions":{"logid":"provider-log-123","connect_id":"provider-connect-456"}}}
```

```json
{"payload":{"additions":{"logid":"provider-log-123"}}}
```

```json
{"header":{"name":"TranscriptionStarted","task_id":"task-001"}}
```

## Logs and troubleshooting

The final `OPENAPI_RESPONSE_RETURN` record keeps `requestId`, `bellaTraceId`,
`endpoint`, `model`, `channelRequestId`, `upstreamLogId`, and
`upstreamConnectId` together. `X-Tt-Logid` is also retained in the existing
`channelRequestId` field when it is empty. An existing provider-specific
channel request identifier, such as a Tencent voice ID, is preserved.

The structured JSON emitted by `ConsoleLogRepo` also includes the
`StartTranscription` request in `request`: `header.task_id` (including a
gateway-generated task ID), `header.message_id`, and `payload` parameters
such as `format`, `sample_rate`, punctuation, inverse text normalization,
and speaker options. These are recorded alongside `requestId`,
`bellaTraceId`, `model`, `channelCode`, `upstreamLogId`, and
`upstreamConnectId`. The request is captured before upstream processing;
API keys and binary audio frames are not included. This is a final request
record, emitted when the task completes or the connection closes, not a
separate billing event at handshake time.

Header collection is best-effort. Providers that do not return either header,
or return an empty value, continue with the historical response shape and
connection behavior. Troubleshoot by starting with `requestId` or
`bellaTraceId`, then narrowing by `channelRequestId`, `endpoint`, and `model`;
use `logid` and `connect_id` when contacting the upstream provider.

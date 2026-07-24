package com.ke.bella.openapi.protocol.tts.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.utils.DateTimeUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealtimeTtsMessage {
    public static final int PROTOCOL_VERSION = 1;
    public static final int STATUS_SUCCESS = 20000000;
    public static final int STATUS_CLIENT_ERROR = 40000000;
    public static final int STATUS_SERVER_ERROR = 50000000;

    private RealtimeTtsHeader header;
    private RealtimeTtsPayload payload;

    public String name() {
        return header == null ? null : header.getName();
    }

    public String taskId() {
        return header == null ? null : header.getTaskId();
    }

    public Integer version() {
        return header == null || header.getVersion() == null ? PROTOCOL_VERSION : header.getVersion();
    }

    public static RealtimeTtsMessage response(RealtimeTtsEventType eventType, String sessionId, String taskId, Integer sequence,
            int status, String statusMessage, RealtimeTtsPayload payload) {
        return RealtimeTtsMessage.builder()
                .header(RealtimeTtsHeader.builder()
                        .name(eventType.getValue())
                        .messageId("msg_" + UUID.randomUUID().toString().replace("-", ""))
                        .sessionId(sessionId)
                        .taskId(taskId)
                        .sequence(sequence)
                        .timestamp(DateTimeUtils.getCurrentMills())
                        .status(status)
                        .statusMessage(statusMessage)
                        .version(PROTOCOL_VERSION)
                        .build())
                .payload(payload == null ? RealtimeTtsPayload.builder().build() : payload)
                .build();
    }

    public static RealtimeTtsMessage started(String sessionId, String taskId, RealtimeTtsCapability capability) {
        return response(RealtimeTtsEventType.SPEECH_STARTED, sessionId, taskId, null,
                STATUS_SUCCESS, "Success", RealtimeTtsPayload.builder().capabilities(capability).build());
    }

    public static RealtimeTtsMessage audioDelta(String sessionId, String taskId, int sequence, String audioId, RealtimeTtsPayload frozen,
            int byteLength, Long durationMs, Boolean isFinal) {
        return response(RealtimeTtsEventType.SPEECH_AUDIO_DELTA, sessionId, taskId, sequence, STATUS_SUCCESS, "Success",
                RealtimeTtsPayload.builder()
                        .audioId(audioId)
                        .format(frozen.getFormat())
                        .sampleRate(frozen.getSampleRate())
                        .channels(frozen.getChannels())
                        .encoding(frozen.getEncoding())
                        .byteLength(byteLength)
                        .durationMs(durationMs)
                        .isFinal(isFinal)
                        .build());
    }

    public static RealtimeTtsMessage completed(String sessionId, String taskId, Long audioDurationMs, Map<String, Object> usage,
            Map<String, Object> metrics) {
        return response(RealtimeTtsEventType.SPEECH_COMPLETED, sessionId, taskId, null, STATUS_SUCCESS, "Success",
                RealtimeTtsPayload.builder()
                        .audioDurationMs(audioDurationMs)
                        .usage(usage == null ? new HashMap<>() : usage)
                        .metrics(metrics == null ? new HashMap<>() : metrics)
                        .build());
    }

    public static RealtimeTtsMessage cancelled(String sessionId, String taskId, String reason) {
        return response(RealtimeTtsEventType.SPEECH_CANCELLED, sessionId, taskId, null, STATUS_SUCCESS, "Cancelled",
                RealtimeTtsPayload.builder().reason(reason).build());
    }

    public static RealtimeTtsMessage failed(String sessionId, String taskId, int status, String message, String code,
            String providerCode, String providerMessage) {
        return response(RealtimeTtsEventType.SPEECH_FAILED, sessionId, taskId, null, status, message,
                RealtimeTtsPayload.builder()
                        .error(RealtimeTtsPayload.Error.builder()
                                .code(code)
                                .message(message)
                                .providerCode(providerCode)
                                .providerMessage(providerMessage)
                                .build())
                        .build());
    }

    public static RealtimeTtsMessage pong(String sessionId, String taskId) {
        return response(RealtimeTtsEventType.PONG, sessionId, taskId, null, STATUS_SUCCESS, "Success", null);
    }
}

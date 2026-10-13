package com.ke.bella.openapi.protocol.live;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum LiveSessionEventType {
    SESSION_START("session.start", true),
    SETUP("setup", true),
    SESSION_UPDATE("session.update", true),
    INPUT_AUDIO_APPEND("session.input_audio.append", true),
    INPUT_VIDEO_APPEND("session.input_video.append", true),
    INPUT_TEXT_APPEND("session.input_text.append", true),
    TOOL_RESULT("tool.result", true),
    SESSION_CLOSE("session.close", true),
    SESSION_STARTED("session.started", false),
    SESSION_UPDATED("session.updated", false),
    INPUT_TRANSCRIPT_DELTA("session.input_transcript.delta", false),
    OUTPUT_TRANSCRIPT_DELTA("session.output_transcript.delta", false),
    OUTPUT_AUDIO_DELTA("session.output_audio.delta", false),
    OUTPUT_TEXT_DELTA("session.output_text.delta", false),
    TOOL_CALL("tool.call", false),
    TOOL_CANCELLED("tool.cancelled", false),
    RESPONSE_INTERRUPTED("response.interrupted", false),
    RESPONSE_DONE("response.done", false),
    USAGE_UPDATED("usage.updated", false),
    SESSION_RESUMPTION("session.resumption", false),
    SESSION_GOAWAY("session.goaway", false),
    SESSION_CLOSED("session.closed", false),
    ERROR("error", false);

    private final String value;
    private final boolean clientEvent;

    LiveSessionEventType(String value, boolean clientEvent) {
        this.value = value;
        this.clientEvent = clientEvent;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public boolean isClientEvent() {
        return clientEvent;
    }

    @JsonCreator
    public static LiveSessionEventType fromString(String value) {
        return Arrays.stream(values()).filter(type -> type.value.equals(value)).findFirst().orElse(null);
    }
}

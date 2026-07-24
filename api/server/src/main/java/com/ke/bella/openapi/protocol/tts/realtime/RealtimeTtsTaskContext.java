package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import lombok.Data;
import okhttp3.WebSocket;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class RealtimeTtsTaskContext {
    public enum State {
        STARTING,
        SPEECH_STARTED,
        FINISHING,
        COMPLETED
    }

    private String sessionId;
    private String taskId;
    private String providerSessionId;
    private RealtimeTtsMessage startRequest;
    private RealtimeTtsPayload frozenPayload;
    private RealtimeTtsCapability capability;
    private EndpointProcessData processData;
    private State state = State.STARTING;
    private long startMillis;
    private long upstreamConnectStartMillis;
    private Long upstreamSessionStartSentMillis;
    private Long firstAudioMillis;
    private long inputCharacters;
    private long audioDurationMs;
    private long audioBytes;
    private int audioFrames;
    private WebSocket upstream;
    private RealtimeTtsUpstreamConnection upstreamConnection;
    private Callbacks.WebSocketCallback callback;
    private boolean providerSessionFailure;
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private final Map<String, Object> metrics = new HashMap<>();

    public int nextSequence() {
        return sequence.incrementAndGet();
    }
}

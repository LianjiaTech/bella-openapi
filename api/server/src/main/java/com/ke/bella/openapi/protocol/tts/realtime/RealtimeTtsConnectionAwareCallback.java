package com.ke.bella.openapi.protocol.tts.realtime;

public interface RealtimeTtsConnectionAwareCallback {
    void setConnection(RealtimeTtsUpstreamConnection connection);
}

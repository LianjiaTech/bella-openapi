package com.ke.bella.openapi.protocol.live.session;

public class LiveProviderEvent {
    private final String type;
    private final int payloadCharacters;
    private final int normalizedMessageCount;
    private final long receivedNanos;
    private final long convertedNanos;

    public LiveProviderEvent(String type, int payloadCharacters, int normalizedMessageCount, long receivedNanos, long convertedNanos) {
        this.type = type;
        this.payloadCharacters = payloadCharacters;
        this.normalizedMessageCount = normalizedMessageCount;
        this.receivedNanos = receivedNanos;
        this.convertedNanos = convertedNanos;
    }

    public String getType() {
        return type;
    }

    public int getPayloadCharacters() {
        return payloadCharacters;
    }

    public int getNormalizedMessageCount() {
        return normalizedMessageCount;
    }

    public long getReceivedNanos() {
        return receivedNanos;
    }

    public long getConvertedNanos() {
        return convertedNanos;
    }
}

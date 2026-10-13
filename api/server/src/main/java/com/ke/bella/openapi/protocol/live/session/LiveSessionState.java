package com.ke.bella.openapi.protocol.live.session;

public enum LiveSessionState {
    AUTHENTICATED,
    WAITING_START,
    ROUTING,
    CONNECTING_PROVIDER,
    ACTIVE,
    CLOSING,
    CLOSED
}

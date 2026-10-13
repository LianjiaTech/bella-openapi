package com.ke.bella.openapi.protocol.live.session;

import com.ke.bella.openapi.protocol.live.LiveSessionMessage;

public interface LiveSessionListener {
    void onProviderMessage(LiveSessionMessage message);

    default void onProviderEvent(LiveProviderEvent event) {
    }

    default void onProviderClosing(int code, String reason) {
    }

    default void onProviderMessage(LiveSessionMessage message, LiveProviderEvent event) {
        onProviderMessage(message);
    }

    void onProviderClosed(int code, String reason);

    void onProviderFailure(Throwable throwable);
}

package com.ke.bella.openapi.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.springframework.web.socket.SubProtocolCapable;

import com.ke.bella.openapi.protocol.realtime.RealTimeHandler;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsHandler;

public class ClientSecretWebSocketProtocolTest {

    @Test
    public void realtimeAsrNegotiatesStableApplicationProtocol() {
        Object handler = mock(RealTimeHandler.class, CALLS_REAL_METHODS);
        assertThat(handler)
                .as("ASR handler must select bella-realtime instead of echoing the credential protocol")
                .isInstanceOf(SubProtocolCapable.class);
        assertThat(((SubProtocolCapable) handler).getSubProtocols()).containsExactly("bella-realtime");
    }

    @Test
    public void realtimeTtsNegotiatesStableApplicationProtocol() {
        Object handler = mock(RealtimeTtsHandler.class, CALLS_REAL_METHODS);
        assertThat(handler)
                .as("TTS handler must select bella-realtime instead of echoing the credential protocol")
                .isInstanceOf(SubProtocolCapable.class);
        assertThat(((SubProtocolCapable) handler).getSubProtocols()).containsExactly("bella-realtime");
    }
}

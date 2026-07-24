package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Slf4j
public class RealtimeTtsClientSender {
    private final WebSocketSession session;
    private final Object lock = new Object();

    public RealtimeTtsClientSender(WebSocketSession session) {
        this.session = session;
    }

    public boolean sendText(RealtimeTtsMessage message) {
        synchronized (lock) {
            return sendTextLocked(message);
        }
    }

    public boolean sendAudioDelta(RealtimeTtsMessage delta, byte[] audio) {
        synchronized (lock) {
            return sendTextLocked(delta) && sendBinaryLocked(audio);
        }
    }

    private boolean sendTextLocked(RealtimeTtsMessage message) {
        if(!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(JacksonUtils.serialize(message)));
            return true;
        } catch (IOException e) {
            log.warn("send realtime tts text frame failed: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean sendBinaryLocked(byte[] audio) {
        if(!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new BinaryMessage(audio));
            return true;
        } catch (IOException e) {
            log.warn("send realtime tts binary frame failed: {}", e.getMessage(), e);
            return false;
        }
    }
}

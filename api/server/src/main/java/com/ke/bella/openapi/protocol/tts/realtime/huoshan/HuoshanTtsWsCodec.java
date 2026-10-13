package com.ke.bella.openapi.protocol.tts.realtime.huoshan;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsMessage;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsPayload;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okio.ByteString;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HuoshanTtsWsCodec {
    public static final int PROTOCOL_VERSION = 0b0001;
    public static final int DEFAULT_HEADER_SIZE = 0b0001;
    public static final int FULL_CLIENT_REQUEST = 0b0001;
    public static final int AUDIO_ONLY_RESPONSE = 0b1011;
    public static final int FULL_SERVER_RESPONSE = 0b1001;
    public static final int ERROR_INFORMATION = 0b1111;
    public static final int MSG_TYPE_FLAG_WITH_EVENT = 0b100;
    public static final int NO_SERIALIZATION = 0b0000;
    public static final int JSON = 0b0001;
    public static final int COMPRESSION_NO = 0b0000;

    public static final int EVENT_NONE = 0;
    public static final int EVENT_START_CONNECTION = 1;
    public static final int EVENT_FINISH_CONNECTION = 2;
    public static final int EVENT_CONNECTION_STARTED = 50;
    public static final int EVENT_CONNECTION_FAILED = 51;
    public static final int EVENT_START_SESSION = 100;
    public static final int EVENT_CANCEL_SESSION = 101;
    public static final int EVENT_FINISH_SESSION = 102;
    public static final int EVENT_SESSION_STARTED = 150;
    public static final int EVENT_SESSION_CANCELED = 151;
    public static final int EVENT_SESSION_FINISHED = 152;
    public static final int EVENT_SESSION_FAILED = 153;
    public static final int EVENT_TASK_REQUEST = 200;
    public static final int EVENT_TTS_RESPONSE = 352;
    public static final int EVENT_TTS_SUBTITLE = 364;

    public ByteString startConnectionFrame() {
        return frame(EVENT_START_CONNECTION, null, "{}".getBytes(StandardCharsets.UTF_8));
    }

    public ByteString finishConnectionFrame() {
        return frame(EVENT_FINISH_CONNECTION, null, "{}".getBytes(StandardCharsets.UTF_8));
    }

    public ByteString startSessionFrame(String sessionId, RealtimeTtsPayload payload) {
        return frame(EVENT_START_SESSION, sessionId, JacksonUtils.toByte(new PayloadJ(EVENT_START_SESSION, ReqParams.session(payload))));
    }

    public ByteString taskRequestFrame(String sessionId, RealtimeTtsMessage request) {
        String text = request.getPayload() == null ? "" : request.getPayload().getText();
        String textType = request.getPayload() == null ? "plain" : request.getPayload().getTextType();
        return frame(EVENT_TASK_REQUEST, sessionId, JacksonUtils.toByte(new PayloadJ(EVENT_TASK_REQUEST, ReqParams.text(text, textType))));
    }

    public ByteString finishSessionFrame(String sessionId) {
        return frame(EVENT_FINISH_SESSION, sessionId, "{}".getBytes(StandardCharsets.UTF_8));
    }

    public ByteString cancelSessionFrame(String sessionId) {
        return frame(EVENT_CANCEL_SESSION, sessionId, "{}".getBytes(StandardCharsets.UTF_8));
    }

    public HuoshanTtsWsEvent parse(byte[] bytes) {
        try {
            if(bytes == null || bytes.length < 4) {
                return HuoshanTtsWsEvent.error("bad_frame", "frame is shorter than header");
            }
            Header header = Header.parse(bytes);
            if(header.protocolVersion != PROTOCOL_VERSION) {
                return HuoshanTtsWsEvent.error("unsupported_protocol", "unsupported protocol version: " + header.protocolVersion);
            }
            if(header.headerSize < DEFAULT_HEADER_SIZE || bytes.length < header.headerSize * 4) {
                return HuoshanTtsWsEvent.error("bad_header", "invalid header size");
            }
            if(header.messageCompression != COMPRESSION_NO) {
                return HuoshanTtsWsEvent.error("unsupported_compression", "unsupported compression: " + header.messageCompression);
            }
            int offset = header.headerSize * 4;
            HuoshanTtsWsEvent event = new HuoshanTtsWsEvent();
            event.setHeader(header);
            if(header.messageType == ERROR_INFORMATION) {
                ReadInt errorCode = readInt(bytes, offset);
                offset = errorCode.nextOffset;
                event.setEvent(EVENT_NONE);
                event.setProviderCode(String.valueOf(errorCode.value));
                ReadBytes payload = readSizedBytes(bytes, offset);
                event.setPayload(payload.bytes);
                event.setProviderMessage(new String(payload.bytes, StandardCharsets.UTF_8));
                event.setError(true);
                return event;
            }
            if(header.messageType != FULL_SERVER_RESPONSE && header.messageType != AUDIO_ONLY_RESPONSE) {
                return HuoshanTtsWsEvent.error("unsupported_message_type", "unsupported message type: " + header.messageType);
            }
            if(header.messageTypeSpecificFlags == MSG_TYPE_FLAG_WITH_EVENT) {
                ReadInt eventId = readInt(bytes, offset);
                event.setEvent(eventId.value);
                offset = eventId.nextOffset;
            }
            if(event.getEvent() == EVENT_CONNECTION_STARTED) {
                ReadBytes connectionId = readSizedBytes(bytes, offset);
                offset = connectionId.nextOffset;
                event.setConnectionId(new String(connectionId.bytes, StandardCharsets.UTF_8));
                readPayloadIfPresent(bytes, offset, event);
                return event;
            }
            if(event.getEvent() == EVENT_CONNECTION_FAILED) {
                ReadBytes connectionId = readSizedBytes(bytes, offset);
                offset = connectionId.nextOffset;
                event.setConnectionId(new String(connectionId.bytes, StandardCharsets.UTF_8));
                readMeta(bytes, offset, event);
                return event;
            }
            if(event.getEvent() != EVENT_NONE) {
                ReadBytes session = readSizedBytes(bytes, offset);
                offset = session.nextOffset;
                event.setSessionId(new String(session.bytes, StandardCharsets.UTF_8));
            }
            if(event.getEvent() == EVENT_SESSION_STARTED || event.getEvent() == EVENT_SESSION_FINISHED ||
                    event.getEvent() == EVENT_SESSION_FAILED || event.getEvent() == EVENT_SESSION_CANCELED) {
                readMeta(bytes, offset, event);
            } else {
                readPayloadIfPresent(bytes, offset, event);
            }
            if(event.getEvent() == EVENT_SESSION_FAILED || event.getEvent() == EVENT_CONNECTION_FAILED) {
                fillProviderError(event);
            }
            return event;
        } catch (Exception e) {
            return HuoshanTtsWsEvent.error("bad_frame", e.getMessage());
        }
    }

    private ByteString frame(int event, String sessionId, byte[] payload) {
        byte[] header = new Header(PROTOCOL_VERSION, DEFAULT_HEADER_SIZE, FULL_CLIENT_REQUEST, MSG_TYPE_FLAG_WITH_EVENT, JSON,
                COMPRESSION_NO, 0).bytes();
        byte[] optional = optional(event, sessionId);
        byte[] payloadSize = intToBytes(payload.length);
        byte[] out = new byte[header.length + optional.length + payloadSize.length + payload.length];
        int offset = 0;
        System.arraycopy(header, 0, out, offset, header.length);
        offset += header.length;
        System.arraycopy(optional, 0, out, offset, optional.length);
        offset += optional.length;
        System.arraycopy(payloadSize, 0, out, offset, payloadSize.length);
        offset += payloadSize.length;
        System.arraycopy(payload, 0, out, offset, payload.length);
        return ByteString.of(out);
    }

    private byte[] optional(int event, String sessionId) {
        byte[] eventBytes = intToBytes(event);
        if(sessionId == null) {
            return eventBytes;
        }
        byte[] sessionIdBytes = sessionId.getBytes(StandardCharsets.UTF_8);
        byte[] sessionIdSize = intToBytes(sessionIdBytes.length);
        byte[] out = new byte[eventBytes.length + sessionIdSize.length + sessionIdBytes.length];
        System.arraycopy(eventBytes, 0, out, 0, eventBytes.length);
        System.arraycopy(sessionIdSize, 0, out, eventBytes.length, sessionIdSize.length);
        System.arraycopy(sessionIdBytes, 0, out, eventBytes.length + sessionIdSize.length, sessionIdBytes.length);
        return out;
    }

    private void readPayloadIfPresent(byte[] bytes, int offset, HuoshanTtsWsEvent event) {
        if(offset >= bytes.length) {
            return;
        }
        ReadBytes payload = readSizedBytes(bytes, offset);
        event.setPayload(payload.bytes);
    }

    private void readMeta(byte[] bytes, int offset, HuoshanTtsWsEvent event) {
        if(offset >= bytes.length) {
            return;
        }
        ReadBytes meta = readSizedBytes(bytes, offset);
        event.setMetaJson(new String(meta.bytes, StandardCharsets.UTF_8));
    }

    private void fillProviderError(HuoshanTtsWsEvent event) {
        Map<String, Object> meta = JacksonUtils.toMap(event.getMetaJson());
        if(meta == null) {
            return;
        }
        Object code = meta.get("status_code");
        Object message = meta.get("message");
        event.setProviderCode(code == null ? null : String.valueOf(code));
        event.setProviderMessage(message == null ? null : String.valueOf(message));
        event.setError(true);
    }

    private ReadBytes readSizedBytes(byte[] bytes, int offset) {
        ReadInt size = readInt(bytes, offset);
        if(size.value < 0 || size.nextOffset + size.value > bytes.length) {
            throw new IllegalArgumentException("payload out of bounds");
        }
        byte[] out = new byte[size.value];
        System.arraycopy(bytes, size.nextOffset, out, 0, size.value);
        return new ReadBytes(out, size.nextOffset + size.value);
    }

    private ReadInt readInt(byte[] bytes, int offset) {
        if(offset + 4 > bytes.length) {
            throw new IllegalArgumentException("int out of bounds");
        }
        return new ReadInt(bytesToInt(bytes, offset), offset + 4);
    }

    public static int bytesToInt(byte[] src, int offset) {
        return ((src[offset] & 0xFF) << 24)
                | ((src[offset + 1] & 0xff) << 16)
                | ((src[offset + 2] & 0xff) << 8)
                | ((src[offset + 3] & 0xff));
    }

    public static byte[] intToBytes(int a) {
        return new byte[] { (byte) ((a >> 24) & 0xFF), (byte) ((a >> 16) & 0xFF), (byte) ((a >> 8) & 0xFF), (byte) (a & 0xFF) };
    }

    @Data
    @AllArgsConstructor
    static class ReadInt {
        int value;
        int nextOffset;
    }

    @Data
    @AllArgsConstructor
    static class ReadBytes {
        byte[] bytes;
        int nextOffset;
    }

    @Data
    @AllArgsConstructor
    public static class Header {
        int protocolVersion;
        int headerSize;
        int messageType;
        int messageTypeSpecificFlags;
        int serializationMethod;
        int messageCompression;
        int reserved;

        byte[] bytes() {
            return new byte[] {
                    (byte) ((protocolVersion << 4) | headerSize),
                    (byte) ((messageType << 4) | messageTypeSpecificFlags),
                    (byte) ((serializationMethod << 4) | messageCompression),
                    (byte) reserved
            };
        }

        static Header parse(byte[] bytes) {
            final byte num = 0b00001111;
            return new Header((bytes[0] >> 4) & num, bytes[0] & num, (bytes[1] >> 4) & num, bytes[1] & num,
                    (bytes[2] >> 4) & num, bytes[2] & num, bytes[3]);
        }
    }

    @Data
    public static class HuoshanTtsWsEvent {
        private Header header;
        private int event;
        private String sessionId;
        private String connectionId;
        private String metaJson;
        private byte[] payload;
        private boolean error;
        private String providerCode;
        private String providerMessage;

        static HuoshanTtsWsEvent error(String code, String message) {
            HuoshanTtsWsEvent event = new HuoshanTtsWsEvent();
            event.error = true;
            event.providerCode = code;
            event.providerMessage = message;
            return event;
        }
    }

    @Data
    @AllArgsConstructor
    public static class PayloadJ {
        private int event;
        @JsonProperty("req_params")
        private ReqParams reqParams;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReqParams {
        private static final Set<String> RESERVED_SESSION_KEYS = reservedSessionKeys();

        private String text;
        private String speaker;
        @JsonProperty("text_type")
        private String textType;
        @JsonProperty("audio_params")
        private Map<String, Object> audioParams;
        private String additions;
        private Map<String, Object> extensions;

        @JsonAnyGetter
        public Map<String, Object> getExtensions() {
            return extensions == null ? Collections.emptyMap() : extensions;
        }

        static ReqParams session(RealtimeTtsPayload payload) {
            Map<String, Object> audio = new HashMap<>();
            audio.put("format", toHuoshanFormat(payload.getFormat()));
            audio.put("sample_rate", payload.getSampleRate());
            audio.put("channels", payload.getChannels());
            audio.put("encoding", payload.getEncoding());
            Number speechRate = speedToSpeechRate(payload.getSpeed());
            if(speechRate != null) {
                audio.put("speech_rate", speechRate);
            }
            Number loudnessRate = ratioToRate(payload.getVolume());
            if(loudnessRate != null) {
                audio.put("loudness_rate", loudnessRate);
            }
            if(Boolean.TRUE.equals(payload.getEnableTimestamp())) {
                audio.put("enable_timestamp", true);
                audio.put("enable_subtitle", true);
            }
            Map<String, Object> additions = null;
            Number pitch = ratioToPitch(payload.getPitch());
            if(pitch != null) {
                Map<String, Object> postProcess = new HashMap<>();
                postProcess.put("pitch", pitch);
                additions = new HashMap<>();
                additions.put("post_process", postProcess);
            }
            Map<String, Object> extraBody = payload.getExtraBody();
            Map<String, Object> extensions = extensions(extraBody);
            additions = mergeAdditions(additions, extraBody == null ? null : extraBody.get("additions"));
            applyStandardPitch(additions, pitch);
            return ReqParams.builder()
                    .speaker(payload.getVoice())
                    .textType(payload.getTextType())
                    .audioParams(audio)
                    .additions(serializeAdditions(additions))
                    .extensions(extensions)
                    .build();
        }

        static ReqParams text(String text, String textType) {
            return ReqParams.builder().text(text).textType(textType == null ? "plain" : textType).build();
        }

        private static Number speedToSpeechRate(Double speed) {
            if(speed == null || Double.compare(speed, 1.0) == 0) {
                return null;
            }
            int speechRate = (int) Math.round((speed - 1.0) * 100);
            return Math.max(-50, Math.min(100, speechRate));
        }

        private static String toHuoshanFormat(String format) {
            if("opus".equalsIgnoreCase(format)) {
                return "ogg_opus";
            }
            return format;
        }

        private static Number ratioToRate(Double ratio) {
            if(ratio == null || Double.compare(ratio, 1.0) == 0) {
                return null;
            }
            int rate = (int) Math.round((ratio - 1.0) * 100);
            return Math.max(-50, Math.min(100, rate));
        }

        private static Number ratioToPitch(Double ratio) {
            if(ratio == null || Double.compare(ratio, 1.0) == 0) {
                return null;
            }
            int pitch = (int) Math.round((ratio - 1.0) * 100);
            return Math.max(-12, Math.min(12, pitch));
        }

        private static Map<String, Object> extensions(Map<String, Object> extraBody) {
            if(extraBody == null || extraBody.isEmpty()) {
                return null;
            }
            Map<String, Object> extensions = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : extraBody.entrySet()) {
                if(!RESERVED_SESSION_KEYS.contains(entry.getKey())) {
                    extensions.put(entry.getKey(), copyValue(entry.getValue()));
                }
            }
            return extensions.isEmpty() ? null : extensions;
        }

        private static Map<String, Object> mergeAdditions(Map<String, Object> standardAdditions, Object extraAdditions) {
            Map<String, Object> merged = copyMap(standardAdditions);
            if(extraAdditions instanceof Map) {
                deepMerge(merged, (Map<?, ?>) extraAdditions);
            }
            return merged == null || merged.isEmpty() ? null : merged;
        }

        private static String serializeAdditions(Map<String, Object> additions) {
            return additions == null || additions.isEmpty() ? null : JacksonUtils.serialize(additions);
        }

        @SuppressWarnings("unchecked")
        private static void applyStandardPitch(Map<String, Object> additions, Number pitch) {
            if(additions == null || pitch == null) {
                return;
            }
            Object postProcess = additions.get("post_process");
            Map<String, Object> postProcessMap;
            if(postProcess instanceof Map) {
                postProcessMap = copyMap((Map<String, Object>) postProcess);
            } else {
                postProcessMap = new LinkedHashMap<>();
            }
            postProcessMap.put("pitch", pitch);
            additions.put("post_process", postProcessMap);
        }

        private static void deepMerge(Map<String, Object> target, Map<?, ?> source) {
            if(source == null) {
                return;
            }
            if(target == null) {
                throw new IllegalArgumentException("target must not be null");
            }
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if(entry.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(entry.getKey());
                Object current = target.get(key);
                Object incoming = entry.getValue();
                if(current instanceof Map && incoming instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> currentMap = (Map<String, Object>) current;
                    deepMerge(currentMap, (Map<?, ?>) incoming);
                } else {
                    target.put(key, copyValue(incoming));
                }
            }
        }

        private static Map<String, Object> copyMap(Map<String, Object> source) {
            if(source == null) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                copy.put(entry.getKey(), copyValue(entry.getValue()));
            }
            return copy;
        }

        private static Object copyValue(Object value) {
            if(value instanceof Map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if(entry.getKey() != null) {
                        copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
                    }
                }
                return copy;
            }
            if(value instanceof List) {
                List<Object> copy = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    copy.add(copyValue(item));
                }
                return copy;
            }
            return value;
        }

        private static Set<String> reservedSessionKeys() {
            Set<String> keys = new HashSet<>();
            keys.add("text");
            keys.add("speaker");
            keys.add("text_type");
            keys.add("audio_params");
            keys.add("additions");
            keys.add("req_params");
            keys.add("vendor_options");
            return Collections.unmodifiableSet(keys);
        }
    }
}

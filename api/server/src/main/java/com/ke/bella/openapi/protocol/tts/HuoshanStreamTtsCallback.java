package com.ke.bella.openapi.protocol.tts;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.Data;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;

public class HuoshanStreamTtsCallback implements Callbacks.WebSocketCallback {
    private static final int PROTOCOL_VERSION = 0b0001;
    private static final int DEFAULT_HEADER_SIZE = 0b0001;

    // Message Type:
    private static final int FULL_CLIENT_REQUEST = 0b0001;
    private static final int AUDIO_ONLY_RESPONSE = 0b1011;
    private static final int FULL_SERVER_RESPONSE = 0b1001;
    private static final int ERROR_INFORMATION = 0b1111;

    // Message Type Specific Flags
    private static final int MsgTypeFlagWithEvent = 0b100;
    // Message Serialization
    private static final int NO_SERIALIZATION = 0b0000;
    private static final int JSON = 0b0001;
    // Message Compression
    private static final int COMPRESSION_NO = 0b0000;

    // event

    // 默认事件,对于使用事件的方案，可以通过非0值来校验事件的合法性
    private static final int EVENT_NONE = 0;

    private static final int EVENT_Start_Connection = 1;

    // 上行Connection事件
    private static final int EVENT_FinishConnection = 2;

    // 下行Connection事件
    private static final int EVENT_ConnectionStarted = 50; // 成功建连

    private static final int EVENT_ConnectionFailed = 51; // 建连失败（可能是无法通过权限认证）

    // 上行Session事件
    private static final int EVENT_StartSession = 100;

    private static final int EVENT_FinishSession = 102;

    // 下行Session事件
    private static final int EVENT_SessionStarted = 150;

    private static final int EVENT_SessionCanceled = 151;

    private static final int EVENT_SessionFinished = 152;

    private static final int EVENT_SessionFailed = 153;

    // 上行通用事件
    private static final int EVENT_TaskRequest = 200;

    // 下行TTS事件

    private static final int EVENT_TTSResponse = 352;

    final String sessionId = UUID.randomUUID().toString().replace("-", "");

    final TtsRequest request;
    final Sender byteSender;
    final EndpointProcessData processData;
    final EndpointLogger logger;
    boolean end = false;
    boolean first = true;
    long startTime = DateTimeUtils.getCurrentMills();

    public HuoshanStreamTtsCallback(TtsRequest request, Sender byteSender, EndpointProcessData processData, EndpointLogger logger) {
        this.request = request;
        this.byteSender = byteSender;
        this.processData = processData;
        this.logger = logger;
        processData.setMetrics(new HashMap<>());
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        startConnection(webSocket);
        processData.setChannelRequestId(response.header("X-Tt-Logid"));
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        TTSResponse response = parserResponse(bytes.toByteArray());

        switch (response.optional.event) {
        case EVENT_ConnectionFailed:
        case EVENT_SessionCanceled:
        case EVENT_SessionFailed: {
            String errorStr = response.optional.response_meta_json;
            Map<String, Object> map = JacksonUtils.toMap(errorStr);
            ChannelException exception = new ChannelException.OpenAIException(convertCode((Integer) map.get("status_code")), "channel_error", (String) map.get("message"));
            onError(exception);
            break;
        }
        case EVENT_ConnectionStarted:
            startTTSSession(webSocket, sessionId, request);
            break;
        case EVENT_SessionStarted:
            // 发送文本
            sendMessage(webSocket, request, sessionId);
            // finish
            finishSession(webSocket, sessionId);
            break;
        case EVENT_TTSResponse: {
            if(response.payload == null) {
                break;
            }
            // 输出结果
            if(response.header.message_type == AUDIO_ONLY_RESPONSE) {
                if(first) {
                    processData.getMetrics().put("ttft", DateTimeUtils.getCurrentMills() - startTime);
                    first = false;
                }
                byteSender.send(response.payload);
            }
            break;
        }
        case EVENT_SessionFinished:
            complete();
            finishConnection(webSocket);
            break;
        default:
            break;
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {

    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {

    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        ChannelException exception;
        if(response != null) {
            int code = response.code();
            String msg = response.message();
            exception = new ChannelException.OpenAIException(code, "channel_error", msg);
        } else {
            exception = ChannelException.fromException(t);
        }
        onError(exception);
    }

    @Override
    public boolean started() {
        return true;
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        complete();
    }

    private void complete() {
        if(!end) {
            processData.getMetrics().put("ttlt", DateTimeUtils.getCurrentMills() - startTime);
            byteSender.close();
            log();
            end = true;
        }
    }

    void onError(ChannelException exception) {
        complete();
        processData.setResponse(OpenapiResponse.errorResponse(exception.convertToOpenapiError()));
        log();
    }

    void log() {
        logger.log(processData);
    }

    int convertCode(int statusCode) {
        if(statusCode < 55000000) {
            return 400;
        }
        return 503;
    }

    int bytesToInt(byte[] src) {
        if(src == null || (src.length != 4)) {
            throw new IllegalArgumentException("");
        }
        return ((src[0] & 0xFF) << 24)
                | ((src[1] & 0xff) << 16)
                | ((src[2] & 0xff) << 8)
                | ((src[3] & 0xff));
    }

    static byte[] intToBytes(int a) {
        return new byte[] {
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)

        };
    }

    public static class Header {

        public int protocol_version = PROTOCOL_VERSION;
        public int header_size = DEFAULT_HEADER_SIZE;
        public int message_type;
        public int message_type_specific_flags = MsgTypeFlagWithEvent;
        public int serialization_method = NO_SERIALIZATION;
        public int message_compression = COMPRESSION_NO;
        public int reserved = 0;

        public Header() {
        }

        public Header(int protocol_version, int header_size, int message_type, int message_type_specific_flags,
                int serialization_method, int message_compression, int reserved) {
            this.protocol_version = protocol_version;
            this.header_size = header_size;
            this.message_type = message_type;
            this.message_type_specific_flags = message_type_specific_flags;
            this.serialization_method = serialization_method;
            this.message_compression = message_compression;
            this.reserved = reserved;
        }

        /**
         * 转成 byte 数组
         *
         * @return
         */
        public byte[] getBytes() {
            return new byte[] {
                    // Protocol version | Header size (4x)
                    (byte) ((protocol_version << 4) | header_size),
                    // Message type | Message type specific flags
                    (byte) (message_type << 4 | message_type_specific_flags),
                    // Serialization method | Compression method
                    (byte) ((serialization_method << 4) | message_compression),
                    (byte) reserved
            };
        }
    }

    @Data
    public static class AudioParams {
        @JsonProperty("speech_rate")
        Double speechRete;
        String format;

        public AudioParams(TtsRequest request) {
            this.speechRete = request.speed;
            this.format = request.responseFormat;
        }
    }

    @Data
    public static class ReqParams {
        String text;
        String speaker;
        @JsonProperty("audio_params")
        AudioParams audioParams;

        public ReqParams(TtsRequest request) {
            this.text = request.input;
            if(request.voice == null) {
                this.speaker = "zh_female_shuangkuaisisi_moon_bigtts";
            } else {
                this.speaker = request.voice;
            }
            this.audioParams = new AudioParams(request);
        }
    }

    @Data
    public static class PayloadJ {
        int event;
        @JsonProperty("req_params")
        ReqParams reqParams;

        public PayloadJ(TtsRequest request, int event) {
            this.event = event;
            this.reqParams = new ReqParams(request);
        }
    }

    public static class Optional {

        public int size;
        public int event = EVENT_NONE;
        public String sessionId;

        public int errorCode;
        public int connectionSize;
        public String connectionId;

        public String response_meta_json;

        public Optional(int event, String sessionId) {
            this.event = event;
            this.sessionId = sessionId;
        }

        public Optional() {
        }

        public byte[] getBytes() {
            byte[] bytes = new byte[0];
            if(event != EVENT_NONE) {
                bytes = intToBytes(event);
            }
            if(sessionId != null) {
                byte[] sessionIdSize = intToBytes(sessionId.getBytes().length);
                final byte[] temp = bytes;
                int desPos = 0;
                bytes = new byte[temp.length + sessionIdSize.length + sessionId.getBytes().length];
                System.arraycopy(temp, 0, bytes, desPos, temp.length);
                desPos += temp.length;
                System.arraycopy(sessionIdSize, 0, bytes, desPos, sessionIdSize.length);
                desPos += sessionIdSize.length;
                System.arraycopy(sessionId.getBytes(), 0, bytes, desPos, sessionId.getBytes().length);

            }
            return bytes;
        }
    }

    public static class TTSResponse {

        public Header header;
        public Optional optional;
        public int payloadSize;
        transient public byte[] payload;
    }

    /**
     * 解析响应包
     *
     * @param res
     * 
     * @return
     */
    TTSResponse parserResponse(byte[] res) {
        if(res == null || res.length == 0) {
            return null;
        }
        final TTSResponse response = new TTSResponse();
        Header header = new Header();
        response.header = header;

        // 当符号位为1时进行 >> 运算后高位补1（预期是补0），导致结果错误，所以增加个数再与其& 运算，目的是确保高位是补0.
        final byte num = 0b00001111;
        // header 32 bit=4 byte
        header.protocol_version = (res[0] >> 4) & num;
        header.header_size = res[0] & 0x0f;
        header.message_type = (res[1] >> 4) & num;
        header.message_type_specific_flags = res[1] & 0x0f;
        header.serialization_method = res[2] >> num;
        header.message_compression = res[2] & 0x0f;
        header.reserved = res[3];

        int offset = 4;
        response.optional = new Optional();
        // 正常Response
        if(header.message_type == FULL_SERVER_RESPONSE || header.message_type == AUDIO_ONLY_RESPONSE) {
            // 如果有event
            offset += readEvent(res, header.message_type_specific_flags, response);
            final int event = response.optional.event;
            // 根据 event 类型解析
            switch (event) {
            case EVENT_NONE:
                break;
            case EVENT_ConnectionStarted:
                readConnectStarted(res, response, offset);
                break;
            case EVENT_ConnectionFailed:
                readConnectFailed(res, response, offset);
                break;
            case EVENT_SessionStarted:
            case EVENT_SessionFailed:
            case EVENT_SessionFinished:
                offset += readSessionId(res, response, offset);
                readMetaJson(res, response, offset);
                break;
            default:
                offset += readSessionId(res, response, offset);
                readPayload(res, response, offset);
                break;
            }
        }
        // 错误
        else if(header.message_type == ERROR_INFORMATION) {
            offset += readErrorCode(res, response, offset);
            readPayload(res, response, offset);
        }
        return response;
    }

    void readConnectStarted(byte[] res, TTSResponse response, int start) {
        // 8--11: connection id size
        byte[] b = new byte[4];
        System.arraycopy(res, start, b, 0, b.length);
        start += b.length;
        response.optional.size += b.length;
        response.optional.connectionSize = bytesToInt(b);
        b = new byte[response.optional.connectionSize];
        System.arraycopy(res, start, b, 0, b.length);
        start += b.length;
        response.optional.size += b.length;
        // 12--18: connection id size
        response.optional.connectionId = new String(b);
        readPayload(res, response, start);
    }

    void readConnectFailed(byte[] res, TTSResponse response, int start) {
        // 8--11: connection id size
        byte[] b = new byte[4];
        System.arraycopy(res, start, b, 0, b.length);
        response.optional.size += b.length;
        start += b.length;
        response.optional.connectionSize = bytesToInt(b);
        readMetaJson(res, response, start);
    }

    void readMetaJson(byte[] res, TTSResponse response, int start) {
        byte[] b = new byte[4];
        System.arraycopy(res, start, b, 0, b.length);
        start += b.length;
        response.optional.size += b.length;
        int size = bytesToInt(b);
        b = new byte[size];
        System.arraycopy(res, start, b, 0, b.length);
        response.optional.size += b.length;
        response.optional.response_meta_json = new String(b);
    }

    int readPayload(byte[] res, TTSResponse response, int start) {
        byte[] b = new byte[4];
        System.arraycopy(res, start, b, 0, b.length);
        start += b.length;
        int size = bytesToInt(b);
        response.payloadSize += size;
        b = new byte[size];
        System.arraycopy(res, start, b, 0, b.length);
        response.payload = b;
        return 4 + size;
    }

    int readErrorCode(byte[] res, TTSResponse response, int start) {
        byte[] b = new byte[4];
        System.arraycopy(res, start, b, 0, b.length);
        response.optional.errorCode = bytesToInt(b);
        response.optional.size += b.length;
        return b.length;
    }

    int readEvent(byte[] res, int masTypeFlag, TTSResponse response) {
        if(masTypeFlag == MsgTypeFlagWithEvent) {
            byte[] temp = new byte[4];
            System.arraycopy(res, 4, temp, 0, temp.length);
            int event = bytesToInt(temp);
            response.optional.event = event;
            response.optional.size += 4;
            return temp.length;
        }
        return 0;
    }

    int readSessionId(byte[] res, TTSResponse response, int start) {
        byte[] b = new byte[4];
        System.arraycopy(res, start, b, 0, b.length);
        start += b.length;
        final int size = bytesToInt(b);
        byte[] sessionIdBytes = new byte[size];
        System.arraycopy(res, start, sessionIdBytes, 0, sessionIdBytes.length);
        response.optional.sessionId = new String(sessionIdBytes);
        return b.length + size;
    }

    boolean startConnection(WebSocket webSocket) {
        byte[] header = getHeader();
        byte[] optional = new Optional(EVENT_Start_Connection, null).getBytes();
        byte[] payload = "{}".getBytes();
        return sendEvent(webSocket, header, optional, payload);
    }

    boolean finishConnection(WebSocket webSocket) {
        byte[] header = getHeader();
        byte[] optional = new Optional(EVENT_FinishConnection, null).getBytes();
        byte[] payload = "{}".getBytes();
        return sendEvent(webSocket, header, optional, payload);

    }

    boolean finishSession(WebSocket webSocket, String sessionId) {
        byte[] header = getHeader();
        byte[] optional = new Optional(EVENT_FinishSession, sessionId).getBytes();
        byte[] payload = "{}".getBytes();
        return sendEvent(webSocket, header, optional, payload);
    }

    boolean startTTSSession(WebSocket webSocket, String sessionId, TtsRequest request) {
        byte[] header = getHeader();
        final int event = EVENT_StartSession;
        byte[] optional = new Optional(event, sessionId).getBytes();
        byte[] payload = JacksonUtils.serialize(new PayloadJ(request, event)).getBytes();
        return sendEvent(webSocket, header, optional, payload);
    }

    /**
     * 分段合成音频
     *
     * @param webSocket
     * @param request
     * @param sessionId
     * 
     * @return
     */
    boolean sendMessage(WebSocket webSocket, TtsRequest request, String sessionId) {
        byte[] header = getHeader();
        final int event = EVENT_TaskRequest;
        byte[] optional = new Optional(event, sessionId).getBytes();
        byte[] payload = JacksonUtils.serialize(new PayloadJ(request, event)).getBytes();
        return sendEvent(webSocket, header, optional, payload);
    }

    byte[] getHeader() {
        return new Header(
                PROTOCOL_VERSION,
                FULL_CLIENT_REQUEST,
                DEFAULT_HEADER_SIZE,
                MsgTypeFlagWithEvent,
                JSON,
                COMPRESSION_NO,
                0).getBytes();
    }

    boolean sendEvent(WebSocket webSocket, byte[] header, byte[] optional, byte[] payload) {
        assert webSocket != null;
        assert header != null;
        assert payload != null;
        final byte[] payloadSizeBytes = intToBytes(payload.length);
        byte[] requestBytes = new byte[header.length
                + (optional == null ? 0 : optional.length)
                + payloadSizeBytes.length + payload.length];
        int desPos = 0;
        System.arraycopy(header, 0, requestBytes, desPos, header.length);
        desPos += header.length;
        if(optional != null) {
            System.arraycopy(optional, 0, requestBytes, desPos, optional.length);
            desPos += optional.length;
        }
        System.arraycopy(payloadSizeBytes, 0, requestBytes, desPos, payloadSizeBytes.length);
        desPos += payloadSizeBytes.length;
        System.arraycopy(payload, 0, requestBytes, desPos, payload.length);
        return webSocket.send(ByteString.of(requestBytes));
    }
}

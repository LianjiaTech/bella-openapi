package com.ke.bella.openapi.protocol.live;

import com.ke.bella.openapi.protocol.IModelFeatures;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class LiveModelFeatures implements IModelFeatures {
    private boolean audio_input;
    private boolean audio_output;
    private boolean text_input;
    private boolean text_output;
    private boolean video_input;
    private boolean full_duplex;
    private boolean interruption;
    private boolean function_calling;
    private boolean websocket;
    private boolean webrtc;
    private boolean sideband;
    private boolean session_resumption;

    @Override
    public Map<String, String> description() {
        Map<String, String> description = new LinkedHashMap<>();
        description.put("audio_input", "是否支持音频输入");
        description.put("audio_output", "是否支持音频输出");
        description.put("text_input", "是否支持文本输入");
        description.put("text_output", "是否支持文本输出");
        description.put("video_input", "是否支持视频输入");
        description.put("full_duplex", "是否支持全双工交互");
        description.put("interruption", "是否支持打断");
        description.put("function_calling", "是否支持函数调用");
        description.put("websocket", "是否支持 WebSocket");
        description.put("webrtc", "是否支持 WebRTC");
        description.put("sideband", "是否支持 Sideband");
        description.put("session_resumption", "是否支持会话恢复");
        return description;
    }
}

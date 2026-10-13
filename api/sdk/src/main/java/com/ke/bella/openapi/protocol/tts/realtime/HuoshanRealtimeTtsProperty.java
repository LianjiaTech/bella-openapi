package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class HuoshanRealtimeTtsProperty extends RealtimeTtsProperty implements IProtocolProperty {
    private AuthorizationProperty auth;
    /**
     * Used only when auth is configured for the legacy console.
     */
    private String appId;
    /**
     * Latest console API Key. Used as X-Api-Key when auth is not configured.
     */
    private String accessKey;
    private String resourceId;
    /**
     * Deprecated: use channel url as websocket endpoint.
     */
    private String websocketUrl;

    @Override
    public Map<String, String> description() {
        Map<String, String> desc = new LinkedHashMap<>();
        desc.put("accessKey", "新版控制台 API Key；未配置 auth 时映射为 X-Api-Key");
        desc.put("auth", "旧版控制台鉴权；填写后启用旧版，X-Api-Access-Key 优先取 auth.secret，auth.secret 为空时取 auth.apiKey");
        desc.put("appId", "旧版控制台 APP ID；填写 auth 时必填，映射为 X-Api-App-Id");
        desc.put("resourceId", "火山资源 ID，例如 seed-tts-2.0 或 seed-icl-2.0");
        desc.put("defaultVoice", "默认音色 ID");
        desc.put("defaultContentType", "默认音频格式，例如 pcm、mp3、opus");
        desc.put("defaultSampleRate", "默认采样率，例如 24000");
        desc.put("encodingType", "默认编码，例如 s16le");
        desc.put("connectionReuse", "是否复用上游 WebSocket 连接");
        return desc;
    }
}

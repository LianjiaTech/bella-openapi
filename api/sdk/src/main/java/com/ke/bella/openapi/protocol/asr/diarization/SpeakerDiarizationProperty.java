package com.ke.bella.openapi.protocol.asr.diarization;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;

import java.util.Map;

/**
 * 说话人识别服务的属性配置
 */
@Data
public class SpeakerDiarizationProperty implements IProtocolProperty {
    
    /**
     * 服务部署名称
     */
    private String deployName;
    
    /**
     * 认证配置
     */
    private AuthorizationProperty auth;
    
    /**
     * 计费信息
     */
    private SpeakerDiarizationPriceInfo priceInfo;
    
    /**
     * 编码类型
     */
    private String encodingType = "";
    
    @Override
    public Map<String, String> description() {
        return ImmutableMap.of(
            "deployName", "服务部署名称",
            "auth", "认证配置", 
            "priceInfo", "计费配置",
            "encodingType", "编码类型"
        );
    }
}
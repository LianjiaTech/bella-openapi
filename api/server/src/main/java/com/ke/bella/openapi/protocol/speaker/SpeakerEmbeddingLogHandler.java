package com.ke.bella.openapi.protocol.speaker;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SpeakerEmbeddingLogHandler implements EndpointLogHandler {
    
    @Override
    public void process(EndpointProcessData processData) {
        // Create usage object for speaker embeddings - 基于音频时长
        SpeakerEmbeddingUsage usage = new SpeakerEmbeddingUsage();
        
        if(processData.getResponse() instanceof SpeakerEmbeddingResponse && processData.getResponse().getError() == null) {
            SpeakerEmbeddingResponse response = (SpeakerEmbeddingResponse) processData.getResponse();
            int durationUnits = calculateDurationUnits(response);
            usage.setDurationUnits(durationUnits);
        } else {
            usage.setDurationUnits(0);
        }
        
        long startTime = processData.getRequestTime();
        int ttlt = (int) (DateTimeUtils.getCurrentSeconds() - startTime);
        Map<String, Object> map = new HashMap<>();
        map.put("ttlt", ttlt);
        map.put("duration", usage.getDurationUnits());
        processData.setMetrics(map);
        processData.setUsage(usage);
    }

    private int calculateDurationUnits(SpeakerEmbeddingResponse response) {
        // 将音频时长（秒）转换为毫秒作为统计单位
        return (int) Math.ceil(response.getDuration() * 1000);
    }

    @Override
    public String endpoint() {
        return "/v1/audio/speaker/embedding";
    }
    
    /**
     * Speaker embedding usage统计类
     * 专门用于记录音频时长的使用情况
     */
    public static class SpeakerEmbeddingUsage {
        /** 音频时长单位（毫秒） */
        private int durationUnits;
        
        public int getDurationUnits() { 
            return durationUnits; 
        }
        
        public void setDurationUnits(int durationUnits) { 
            this.durationUnits = durationUnits; 
        }
        
        /**
         * 获取时长（秒）
         */
        public double getDurationSeconds() {
            return durationUnits / 1000.0;
        }
    }
}
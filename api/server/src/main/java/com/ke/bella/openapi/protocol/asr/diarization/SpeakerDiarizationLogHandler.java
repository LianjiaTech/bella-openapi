package com.ke.bella.openapi.protocol.asr.diarization;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SpeakerDiarizationLogHandler implements EndpointLogHandler {
    
    @Override
    public void process(EndpointProcessData processData) {
        // 基于音频时长计费（秒）
        SpeakerDiarizationUsage usage = new SpeakerDiarizationUsage();
        
        if(processData.getResponse() instanceof SpeakerDiarizationResponse && processData.getResponse().getError() == null) {
            SpeakerDiarizationResponse response = (SpeakerDiarizationResponse) processData.getResponse();
            // 音频时长（秒）
            int audioDurationSeconds = (int) Math.ceil(response.getDuration());
            int speakerCount = response.getNumSpeakers();
            usage.setAudioDurationSeconds(audioDurationSeconds);
            usage.setSpeakerCount(speakerCount);
        } else {
            usage.setAudioDurationSeconds(0);
            usage.setSpeakerCount(0);
        }
        
        // 计算处理时间
        long startTime = processData.getRequestTime();
        int ttlt = (int) (DateTimeUtils.getCurrentSeconds() - startTime);
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("ttlt", ttlt);
        metrics.put("audio_duration", usage.getAudioDurationSeconds());
        metrics.put("speakers", usage.getSpeakerCount());
        processData.setMetrics(metrics);
        processData.setDuration(ttlt);
        processData.setUsage(usage);
    }

    @Override
    public String endpoint() {
        return "/v1/audio/speaker/diarization";
    }
    
    /**
     * Speaker diarization usage统计类
     * 基于音频时长（秒）计费
     */
    public static class SpeakerDiarizationUsage {
        /** 音频时长（秒） */
        private int audioDurationSeconds;
        
        /** 识别出的说话人数量 */
        private int speakerCount;
        
        public int getAudioDurationSeconds() { 
            return audioDurationSeconds; 
        }
        
        public void setAudioDurationSeconds(int audioDurationSeconds) { 
            this.audioDurationSeconds = audioDurationSeconds; 
        }
        
        public int getSpeakerCount() {
            return speakerCount;
        }
        
        public void setSpeakerCount(int speakerCount) {
            this.speakerCount = speakerCount;
        }
    }
}
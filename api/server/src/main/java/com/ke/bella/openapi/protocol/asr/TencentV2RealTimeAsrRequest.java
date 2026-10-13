package com.ke.bella.openapi.protocol.asr;

import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;

import lombok.Data;

@Data
public class TencentV2RealTimeAsrRequest {
    private String voiceId;
    private String engineModelType;
    private Integer voiceFormat;
    private Integer inputSampleRate;
    private String hotwordList;
    private String hotwordId;
    private String customizationId;
    private String replaceTextId;
    private Integer needvad;
    private Integer filterDirty;
    private Integer filterModal;
    private Integer filterEmptyResult;
    private Integer convertNumMode;
    private Float noiseThreshold;
    private Integer sentenceStrategy;
    private Integer domain;
    private Integer enableSpeakerContext;
    private String speakerContextId;

    public TencentV2RealTimeAsrRequest(RealTimeMessage request, TencentV2Property property) {
        RealTimeMessage.Payload payload = request.getPayload();
        Map<String, Object> additions = payload != null ? payload.getAdditions() : null;
        this.voiceId = UUID.randomUUID().toString();
        this.engineModelType = resolveEngineModelType(property);
        this.voiceFormat = getVoiceFormat(payload != null ? payload.getFormat() : null);
        this.inputSampleRate = resolveInputSampleRate(payload, voiceFormat);
        this.hotwordList = payload != null ? payload.getHotWords() : null;
        this.hotwordId = payload != null ? payload.getHotWordsTableId() : null;
        this.customizationId = payload != null ? payload.getCustomizationId() : null;
        this.replaceTextId = payload != null ? payload.getVocabularyId() : null;
        this.needvad = intAddition(additions, "needvad");
        this.filterDirty = intAddition(additions, "filter_dirty");
        this.filterModal = intAddition(additions, "filter_modal");
        this.filterEmptyResult = intAddition(additions, "filter_empty_result");
        this.convertNumMode = resolveConvertNumMode(payload, additions);
        this.noiseThreshold = payload != null ? payload.getSpeechNoiseThreshold() : null;
        this.sentenceStrategy = intAddition(additions, "sentence_strategy");
        this.domain = intAddition(additions, "domain");
        this.enableSpeakerContext = intAddition(additions, "enable_speaker_context");
        this.speakerContextId = stringAddition(additions, "speaker_context_id");
    }

    private String resolveEngineModelType(TencentV2Property property) {
        if(property == null || StringUtils.isBlank(property.getEngineModelType())) {
            throw new BizParamCheckException("Tencent V2 ASR channelInfo.engineModelType is required");
        }
        return property.getEngineModelType();
    }

    private Integer resolveInputSampleRate(RealTimeMessage.Payload payload, Integer voiceFormat) {
        if(payload == null || payload.getSampleRate() == null) {
            return null;
        }
        if(voiceFormat != null && voiceFormat == 1 && payload.getSampleRate() == 8000) {
            return 8000;
        }
        return null;
    }

    private Integer resolveConvertNumMode(RealTimeMessage.Payload payload, Map<String, Object> additions) {
        Integer convertNumMode = intAddition(additions, "convert_num_mode");
        if(convertNumMode != null) {
            return convertNumMode;
        }
        if(payload != null && Boolean.FALSE.equals(payload.getEnableInverseTextNormalization())) {
            return 0;
        }
        return null;
    }

    private Integer intAddition(Map<String, Object> additions, String key) {
        Object value = additions != null ? additions.get(key) : null;
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private String stringAddition(Map<String, Object> additions, String key) {
        Object value = additions != null ? additions.get(key) : null;
        return value == null ? null : String.valueOf(value);
    }

    private Integer getVoiceFormat(String format) {
        if("pcm".equalsIgnoreCase(format)) {
            return 1;
        } else if("speex".equalsIgnoreCase(format)) {
            return 4;
        } else if("silk".equalsIgnoreCase(format)) {
            return 6;
        } else if("mp3".equalsIgnoreCase(format)) {
            return 8;
        } else if("opus".equalsIgnoreCase(format)) {
            return 10;
        } else if("wav".equalsIgnoreCase(format)) {
            return 12;
        } else if("m4a".equalsIgnoreCase(format)) {
            return 14;
        } else if("aac".equalsIgnoreCase(format)) {
            return 16;
        }
        return 1;
    }
}

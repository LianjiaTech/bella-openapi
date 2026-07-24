package com.ke.bella.openapi.protocol.asr;

import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import lombok.Getter;

@Getter
public class HuoshanRealTimeAsrRequest {
    // Huoshan-specific fields
    private final boolean async;
    private final String appId;
    private final String token;
    private final String cluster;
    private final byte[] audioData;
    private final int chunkSize;
    private final int intervalMs;
    private final Boolean enable_punc;
    private final Boolean enable_itn;

    // Composition: delegate to AsrRequest for shared fields
    private final AsrRequest asrRequest;

    public HuoshanRealTimeAsrRequest(AsrRequest request, HuoshanProperty property) {
        this.asrRequest = request;
        this.async = false;
        this.appId = property.getAppid();
        this.token = property.getAuth().getSecret();
        this.cluster = property.getDeployName();
        this.audioData = request.getContent();
        this.chunkSize = property.getChunkSize();
        this.intervalMs = property.getIntervalMs();
        this.enable_punc = request.getEnablePunc();
        this.enable_itn = request.getEnableItn() != null ? request.getEnableItn() : request.getConvertNumbers();
    }

    public HuoshanRealTimeAsrRequest(RealTimeMessage request, HuoshanProperty property) {
        this.asrRequest = buildFromRealTime(request);
        this.async = true;
        this.appId = property.getAppid();
        this.token = property.getAuth().getSecret();
        this.cluster = property.getDeployName();
        this.audioData = null;
        this.chunkSize = property.getChunkSize();
        this.intervalMs = property.getIntervalMs();
        this.enable_punc = request.getPayload().getEnablePunctuationPrediction();
        this.enable_itn = request.getPayload().getEnableInverseTextNormalization();
    }

    private static AsrRequest buildFromRealTime(RealTimeMessage request) {
        return AsrRequest.builder()
                .uid("0")
                .format(request.getPayload().getFormat())
                .sampleRate(request.getPayload().getSampleRate())
                .resultType("single")
                .hotWords(request.getPayload().getHotWords())
                .hotWordsTableId(request.getPayload().getHotWordsTableId())
                .enableNonstream(request.getPayload().getEnableNonstream())
                .enableSpeakerInfo(request.getPayload().getEnableSpeakerInfo())
                .enableGenderDetection(request.getPayload().getEnableGenderDetection())
                .endWindowSize(request.getPayload().getEndWindowSize())
                .ssdVersion(request.getPayload().getSsdVersion())
                .build();
    }

    // Delegate getters for AsrRequest fields used by callbacks
    public String getUid() {
        String uid = asrRequest.getUid();
        return uid == null ? "0" : uid;
    }

    public String getDid() {
        return asrRequest.getDid();
    }

    public String getPlatform() {
        return asrRequest.getPlatform();
    }

    public String getSdkVersion() {
        return asrRequest.getSdkVersion();
    }

    public String getAppVersion() {
        return asrRequest.getAppVersion();
    }

    public String getFormat() {
        return asrRequest.getFormat();
    }

    public String getLanguage() {
        return asrRequest.getLanguage();
    }

    public String getCodec() {
        return asrRequest.getCodec();
    }

    public int getSampleRate() {
        return asrRequest.getSampleRate();
    }

    public Integer getBits() {
        return asrRequest.getBits();
    }

    public Integer getChannel() {
        return asrRequest.getChannel();
    }

    public String getResultType() {
        return asrRequest.getResultType();
    }

    public String getHotWords() {
        return asrRequest.getHotWords();
    }

    public String getHotWordsTableId() {
        return asrRequest.getHotWordsTableId();
    }

    public Boolean getEnableNonstream() {
        return asrRequest.getEnableNonstream();
    }

    public Boolean getEnableSpeakerInfo() {
        return asrRequest.getEnableSpeakerInfo();
    }

    public Boolean getEnableGenderDetection() {
        return asrRequest.getEnableGenderDetection();
    }

    public Boolean getEnableDdc() {
        return asrRequest.getEnableDdc();
    }

    public String getOutputZhVariant() {
        return asrRequest.getOutputZhVariant();
    }

    public Boolean getEnableAutoLang() {
        return asrRequest.getEnableAutoLang();
    }

    public Boolean getShowUtterances() {
        return asrRequest.getShowUtterances();
    }

    public Boolean getShowSpeechRate() {
        return asrRequest.getShowSpeechRate();
    }

    public Boolean getShowVolume() {
        return asrRequest.getShowVolume();
    }

    public Boolean getEnableLid() {
        return asrRequest.getEnableLid();
    }

    public Boolean getEnableEmotionDetection() {
        return asrRequest.getEnableEmotionDetection();
    }

    public Boolean getEnableAccelerateText() {
        return asrRequest.getEnableAccelerateText();
    }

    public Double getAccelerateScore() {
        return asrRequest.getAccelerateScore();
    }

    public Integer getVadSegmentDuration() {
        return asrRequest.getVadSegmentDuration();
    }

    public Integer getEndWindowSize() {
        return asrRequest.getEndWindowSize();
    }

    public Integer getForceToSpeechTime() {
        return asrRequest.getForceToSpeechTime();
    }

    public String getSsdVersion() {
        return asrRequest.getSsdVersion();
    }

    public String getSensitiveWordsFilter() {
        return asrRequest.getSensitiveWordsFilter();
    }

    public Boolean getEnablePoiFc() {
        return asrRequest.getEnablePoiFc();
    }

    public Boolean getEnableMusicFc() {
        return asrRequest.getEnableMusicFc();
    }

    public String getBoostingTableName() {
        return asrRequest.getBoostingTableName();
    }

    public String getCorrectTableName() {
        return asrRequest.getCorrectTableName();
    }

    public String getCorrectTableId() {
        return asrRequest.getCorrectTableId();
    }

    public String getContext() {
        return asrRequest.getContext();
    }
}

package com.ke.bella.openapi.protocol.asr;

import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import lombok.Data;

@Data
public class HuoshanRealTimeAsrRequest {
    private boolean async;
    private String uid;
    private String appId;
    private String token;
    private String cluster;
    private String format;
    private int sampleRate;
    private byte[] audioData;
    private int chunkSize;
    private int intervalMs;
    private String resultType; //full single
    private String hotWords; // 热词字符串
    private String hotWordsTableId; // 热词表id

    public HuoshanRealTimeAsrRequest(AsrRequest request, HuoshanProperty property) {
        this.async = false;
        this.uid = "0";
        this.appId = property.getAppid();
        this.token = property.getAuth().getSecret();
        this.cluster = property.getDeployName();
        this.format = request.getFormat();
        this.sampleRate = request.getSampleRate();
        this.audioData = request.getContent();
        this.chunkSize = property.getChunkSize();
        this.intervalMs = property.getIntervalMs();
        this.resultType = "full";
        this.hotWords = request.getHotWords();
        this.hotWordsTableId = request.getHotWordsTableId();
    }

    public HuoshanRealTimeAsrRequest(RealTimeMessage request, HuoshanProperty property) {
        this.async = true;
        this.uid = "0";
        this.appId = property.getAppid();
        this.token = property.getAuth().getSecret();
        this.cluster = property.getDeployName();
        this.format = request.getPayload().getFormat();
        this.sampleRate = request.getPayload().getSampleRate();
        this.chunkSize = property.getChunkSize();
        this.intervalMs = property.getIntervalMs();
        this.resultType = "single";
        this.hotWords = request.getPayload().getHotWords();
        this.hotWordsTableId = request.getPayload().getHotWordsTableId();
    }
}

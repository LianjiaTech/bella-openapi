package com.ke.bella.openapi.protocol.asr;

import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class TencentRealTimeAsrRequest {
	private String voiceId; 			// 音频流全局唯一标识

	private String appid;
	private String secretid;
	private String secretkey;

	private String engineModelType; 	// 引擎模型类型
	private Integer voiceFormat; 		// 音频格式编码：1-pcm
	private Integer inputSampleRate; 	// 采样率


	private Integer wordInfo;    		// 是否显示词级别时间戳
	private String hotwordList; 		// 临时热词表, 格式："腾讯云|10,语音识别|5,ASR|11"，优先值高于 hotwordId
	private String hotwordId;    		// 热词表 ID
	private String replaceTextId; 		// 替换词汇表 ID,  适用于热词和自学习场景也无法解决的极端 case 词组


	public TencentRealTimeAsrRequest(RealTimeMessage request, TencentProperty property) {
		this.voiceId = java.util.UUID.randomUUID().toString();
		this.appid = property.getAppid();
		this.secretid = property.getAuth().getApiKey();
		this.secretkey = property.getAuth().getSecret();
		this.engineModelType = property.getEngineModelType();
		this.voiceFormat = getVoiceFormat(request.getPayload().getFormat());
		this.inputSampleRate = request.getPayload().getSampleRate();

		this.hotwordId = request.getPayload().getHotWordsTableId() != null ? request.getPayload().getHotWordsTableId(): null;
		this.hotwordList = request.getPayload().getHotWords() != null ? request.getPayload().getHotWords(): null;
		this.wordInfo = request.getPayload().getEnableWords() != null ? (request.getPayload().getEnableWords() ? 1 : 0) : null;
		this.replaceTextId = request.getPayload().getVocabularyId()  != null ? request.getPayload().getVocabularyId(): null;
	}


	private Integer getVoiceFormat(String format) {
		if ("pcm".equalsIgnoreCase(format)) {
			return 1;
		} else if ("wav".equalsIgnoreCase(format)) {
			return 2;
		} else if ("opus".equalsIgnoreCase(format)) {
			return 3;
		} else if ("speex".equalsIgnoreCase(format)) {
			return 4;
		} else if ("silk".equalsIgnoreCase(format)) {
			return 5;
		} else if ("mp3".equalsIgnoreCase(format)) {
			return 6;
		} else if ("m4a".equalsIgnoreCase(format)) {
			return 7;
		} else if ("aac".equalsIgnoreCase(format)) {
			return 8;
		}
		return 1; // 默认pcm
	}
}

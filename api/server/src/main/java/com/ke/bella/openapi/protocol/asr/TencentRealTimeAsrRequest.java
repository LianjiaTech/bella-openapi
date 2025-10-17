package com.ke.bella.openapi.protocol.asr;

import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class TencentRealTimeAsrRequest {
	private boolean async; // 是否异步（流式）
	private String voiceId; // 音频流全局唯一标识
	private String appid;
	private String secretid;
	private String secretkey;
	private String engineModelType; // 引擎模型类型
	private String format; // 音频格式
	private Integer voiceFormat; // 音频格式编码：1-pcm
	private Integer sampleRate; // 采样率
	private byte[] audioData; // 音频数据（非流式）
	private int chunkSize; // 数据块大小
	private int intervalMs; // 发送间隔
	private Integer needvad; // 是否需要VAD
	private Integer filterDirty; // 是否过滤脏词
	private Integer filterModal; // 是否过滤语气词
	private Integer filterPunc; // 是否过滤标点符号
	private Integer filterEmpty; // 是否过滤空结果
	private String hotwordList; // 临时热词表
	private Integer convertNumMode; // 数字转换模式
	private Integer wordInfo; // 是否返回词级别时间戳

	public TencentRealTimeAsrRequest(AsrRequest request, TencentProperty property) {
		this.async = false;
		this.voiceId = java.util.UUID.randomUUID().toString();
		this.appid = property.getAppid();
		this.secretid = property.getAuth().getApiKey();
		this.secretkey = property.getAuth().getSecret();
		this.engineModelType = property.getEngineModelType();
		this.format = request.getFormat();
		this.voiceFormat = getVoiceFormat(request.getFormat());
		this.sampleRate = request.getSampleRate();
		this.audioData = request.getContent();
		this.chunkSize = property.getChunkSize();
		this.intervalMs = property.getIntervalMs();
		this.needvad = property.getNeedvad();
		this.hotwordList = property.getHotwordList();
		this.convertNumMode = property.getConvertNumMode();
		this.wordInfo = property.getWordInfo();
	}

	public TencentRealTimeAsrRequest(RealTimeMessage request, TencentProperty property) {
		this.async = true;
		this.voiceId = java.util.UUID.randomUUID().toString();
		this.appid = property.getAppid();
		this.secretid = property.getAuth().getApiKey();
		this.secretkey = property.getAuth().getSecret();

		// 引擎模型类型优先级：请求中的 > 渠道配置的
		if (request.getPayload() != null && request.getPayload().getEngineModelType() != null
				&& !request.getPayload().getEngineModelType().isEmpty()) {
			String requestEngineType = request.getPayload().getEngineModelType();
			// 校验引擎模型类型
			if (isValidEngineModelType(requestEngineType)) {
				this.engineModelType = requestEngineType;
				log.info("使用请求中的引擎模型类型: {}", this.engineModelType);
			} else {
				this.engineModelType = property.getEngineModelType();
				log.warn("无效的引擎模型类型: {}，使用渠道配置的默认值: {}", requestEngineType, this.engineModelType);
			}
		} else {
			this.engineModelType = property.getEngineModelType();
			log.info("使用渠道配置的引擎模型类型: {}", this.engineModelType);
		}

		this.format = request.getPayload().getFormat();
		this.voiceFormat = getVoiceFormat(request.getPayload().getFormat());
		this.sampleRate = request.getPayload().getSampleRate();
		this.chunkSize = property.getChunkSize();
		this.intervalMs = property.getIntervalMs();
		this.needvad = property.getNeedvad();

		// 热词优先级：请求中的热词 > 渠道配置的热词
		// 如果请求中传入了hotWords，使用请求中的；否则使用渠道配置
		if (request.getPayload() != null && request.getPayload().getHotWords() != null
				&& !request.getPayload().getHotWords().isEmpty()) {
			this.hotwordList = request.getPayload().getHotWords();
			log.info("使用请求中的热词: {}", this.hotwordList);
		} else {
			this.hotwordList = property.getHotwordList();
			if (this.hotwordList != null && !this.hotwordList.isEmpty()) {
				log.info("使用渠道配置的临时热词表: {}", this.hotwordList);
			}
		}

		this.convertNumMode = property.getConvertNumMode();
		this.wordInfo = property.getWordInfo();
	}

	/**
	 * 校验引擎模型类型是否有效
	 * @param engineModelType 引擎模型类型
	 * @return 是否有效
	 */
	private boolean isValidEngineModelType(String engineModelType) {
		if (engineModelType == null || engineModelType.trim().isEmpty()) {
			return false;
		}

		// 电话场景
		if ("8k_zh".equals(engineModelType) ||
			"8k_en".equals(engineModelType) ||
			"8k_zh_large".equals(engineModelType)) {
			return true;
		}

		// 非电话场景 - 大模型版
		if ("16k_zh_en".equals(engineModelType) ||
			"16k_zh_large".equals(engineModelType) ||
			"16k_multi_lang".equals(engineModelType)) {
			return true;
		}

		// 非电话场景 - 中文
		if ("16k_zh".equals(engineModelType) ||
			"16k_zh-TW".equals(engineModelType) ||
			"16k_zh_edu".equals(engineModelType) ||
			"16k_zh_medical".equals(engineModelType) ||
			"16k_zh_court".equals(engineModelType) ||
			"16k_yue".equals(engineModelType)) {
			return true;
		}

		// 非电话场景 - 英文
		if ("16k_en".equals(engineModelType) ||
			"16k_en_game".equals(engineModelType) ||
			"16k_en_edu".equals(engineModelType)) {
			return true;
		}

		// 非电话场景 - 其他语种
		if ("16k_ko".equals(engineModelType) ||
			"16k_ja".equals(engineModelType) ||
			"16k_th".equals(engineModelType) ||
			"16k_id".equals(engineModelType) ||
			"16k_vi".equals(engineModelType) ||
			"16k_ms".equals(engineModelType) ||
			"16k_fil".equals(engineModelType) ||
			"16k_pt".equals(engineModelType) ||
			"16k_tr".equals(engineModelType) ||
			"16k_ar".equals(engineModelType) ||
			"16k_es".equals(engineModelType) ||
			"16k_hi".equals(engineModelType) ||
			"16k_fr".equals(engineModelType) ||
			"16k_de".equals(engineModelType)) {
			return true;
		}

		return false;
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

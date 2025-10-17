package com.ke.bella.openapi.protocol.asr;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TencentProperty extends AsrProperty {
	String appid;
	String engineModelType = "16k_zh"; // 引擎模型类型
	int chunkSize = 3200; // 音频数据块大小（字节）
	int intervalMs = 40; // 发送间隔（毫秒），建议40ms
	Integer needvad; // 是否需要VAD，0或1
	String hotwordList; // 临时热词表，格式："腾讯云|10,语音识别|5,ASR|11"
	Integer convertNumMode; // 数字转换模式
	Integer wordInfo; // 是否返回词级别时间戳，0或1
}

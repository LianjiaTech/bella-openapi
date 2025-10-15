package com.ke.bella.openapi.protocol.asr;

import lombok.Data;

@Data
public class TencentProperty extends AsrProperty {
	String appid;
	String engineModelType = "16k_zh"; // 引擎模型类型
	int chunkSize = 3200; // 音频数据块大小（字节）
	int intervalMs = 40; // 发送间隔（毫秒），建议40ms
	Integer needvad; // 是否需要VAD，0或1
	Integer filterDirty; // 是否过滤脏词，0或1
	Integer filterModal; // 是否过滤语气词，0或1
	Integer filterPunc; // 是否过滤标点符号，0或1
	Integer filterEmpty; // 是否过滤空结果，0或1
	String hotwordId; // 热词表ID
	Integer convertNumMode; // 数字转换模式
	Integer wordInfo; // 是否返回词级别时间戳，0或1
}

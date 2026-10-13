package com.ke.bella.openapi.protocol.asr;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TencentProperty extends AsrProperty {
    String appid;
    String engineModelType = "16k_zh"; // 引擎模型类型
}

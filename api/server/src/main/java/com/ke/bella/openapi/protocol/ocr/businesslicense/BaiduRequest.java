package com.ke.bella.openapi.protocol.ocr.businesslicense;

import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduBaseRequest;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 百度营业执照识别请求
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BaiduRequest extends BaiduBaseRequest {

    /**
     * 识别精度，normal 或 high，新版本无需传
     */
    @Builder.Default
    private String accuracy = "normal";

    /**
     * 是否开启风险类型功能，默认 false
     */
    @Builder.Default
    private String riskWarn = "false";

    /**
     * 是否开启质量检测功能（清晰模糊、边框/四角不完整），默认 false
     */
    @Builder.Default
    private String detectQuality = "false";

    /**
     * 是否开启全角符号转换，默认 false
     * false：单位名称、类型、经营范围字段内括号以半角输出
     * true：单位名称、类型、经营范围字段内括号以全角输出
     */
    @Builder.Default
    private String fullwidthShift = "false";
}

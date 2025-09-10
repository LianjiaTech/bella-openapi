package com.ke.bella.openapi.protocol.ocr;

import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OCR协议适配器属性基类
 */
@Data
public class OcrProperty implements IProtocolProperty {
    // 外部渠道支持的图片输入方式配置
    private boolean supportUrl = true;      // 渠道是否支持URL输入
    private boolean supportBase64 = true;   // 渠道是否支持Base64输入
    private boolean supportBinary = true;   // 渠道是否支持二进制输入
    private boolean supportFormData = true; // 渠道是否支持form-data输入

    // 编码类型配置
    private String encodingType = StringUtils.EMPTY;            // 编码类型

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("supportUrl", "是否支持URL输入");
        map.put("supportBase64", "是否支持Base64输入");
        map.put("supportBinary", "是否支持二进制输入");
        map.put("supportFormData", "是否支持form-data输入");
        map.put("encodingType", "编码类型");
        return map;
    }
}

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

    private String encodingType = StringUtils.EMPTY;            // 编码类型

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("encodingType", "编码类型");
        return map;
    }
}

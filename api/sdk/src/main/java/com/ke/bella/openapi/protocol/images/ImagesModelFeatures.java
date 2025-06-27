package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.IModelFeatures;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ImagesModelFeatures implements IModelFeatures {
    private boolean highQuality = false;
    private boolean multipleStyles = false;
    private boolean customSize = false;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("highQuality", "是否支持高质量生成");
        map.put("multipleStyles", "是否支持多种风格");
        map.put("customSize", "是否支持自定义尺寸");
        return map;
    }
}

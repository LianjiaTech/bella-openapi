package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.IModelFeatures;
import lombok.Data;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Data
public class ImagesModelFeatures implements IModelFeatures {
    private boolean highQuality = false;
    private boolean multipleStyles = false;
    private boolean customSize = false;
    private boolean batchGeneration = false;
    private boolean promptRevision = false;

    @Override
    public Map<String, String> description() {
        SortedMap<String, String> map = new TreeMap<>();
        map.put("highQuality", "是否支持高质量生成");
        map.put("multipleStyles", "是否支持多种风格");
        map.put("customSize", "是否支持自定义尺寸");
        map.put("batchGeneration", "是否支持批量生成");
        map.put("promptRevision", "是否支持提示词修订");
        return map;
    }
}

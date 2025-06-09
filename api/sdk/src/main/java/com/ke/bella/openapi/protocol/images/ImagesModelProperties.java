package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.IModelProperties;
import lombok.Data;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Data
public class ImagesModelProperties implements IModelProperties {
    private Integer maxPromptLength = 4000;
    private Integer maxImages = 10;
    private String[] supportedSizes = {"256x256", "512x512", "1024x1024", "1792x1024", "1024x1792"};
    private String[] supportedFormats = {"url", "b64_json"};
    private String[] supportedQualities = {"standard", "hd"};
    private String[] supportedStyles = {"vivid", "natural"};
    private boolean supportCustomSize = false;
    private Integer maxWidth = 2048;
    private Integer maxHeight = 2048;

    @Override
    public Map<String, String> description() {
        SortedMap<String, String> map = new TreeMap<>();
        map.put("maxPromptLength", "最大提示词长度");
        map.put("maxImages", "最大生成图片数量");
        map.put("supportedSizes", "支持的图片尺寸");
        map.put("supportedFormats", "支持的响应格式");
        map.put("supportedQualities", "支持的图片质量");
        map.put("supportedStyles", "支持的图片风格");
        map.put("supportCustomSize", "是否支持自定义尺寸");
        return map;
    }
}

package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Data
public class ImagesProperty implements IProtocolProperty {
    String encodingType = StringUtils.EMPTY;
    Map<String, String> extraHeaders;
    String queueName;
    boolean supportB64Json = true;
    boolean supportUrl = true;
    String defaultResponseFormat = "url";
    String defaultSize = "1024x1024";

    @Override
    public Map<String, String> description() {
        SortedMap<String, String> map = new TreeMap<>();
        map.put("encodingType", "编码类型");
        map.put("extraHeaders", "额外的请求头");
        map.put("queueName", "队列（配置后请求被bella-job-queue服务代理）");
        map.put("supportB64Json", "是否支持base64格式返回");
        map.put("supportUrl", "是否支持URL格式返回");
        map.put("defaultResponseFormat", "默认响应格式");
        map.put("defaultSize", "默认图片尺寸");
        return map;
    }
}

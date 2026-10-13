package com.ke.bella.openapi.protocol.batch;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class HuoshanJobProperty extends BatchProperty {

    private String region;
    private String tosEndpoint;
    private String tosBucket;
    private String inputPrefix;
    private String outputPrefix;
    private String model;
    private String modelVersion;
    private String projectName;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>(super.description());
        map.put("region", "火山地域，例如 cn-beijing");
        map.put("tosEndpoint", "TOS Endpoint，例如 tos-cn-beijing.volces.com");
        map.put("tosBucket", "TOS Bucket");
        map.put("inputPrefix", "输入 JSONL 对象前缀");
        map.put("outputPrefix", "输出 JSONL 目录前缀");
        map.put("model", "方舟模型名称");
        map.put("modelVersion", "方舟模型版本");
        map.put("projectName", "方舟项目名称");
        return map;
    }
}

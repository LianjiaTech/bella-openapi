package com.ke.bella.openapi.protocol.batch;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class VertexBatchProperty extends BatchProperty {

    private String projectId;
    private String location;
    private String model;
    private String gcsBucket;
    private String gcsEndpoint;
    private String inputPrefix;
    private String outputPrefix;
    private String displayNamePrefix;
    private boolean supportSystemInstruction = true;
    private boolean supportThinkConfig = false;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>(super.description());
        map.remove("fileServiceUrl");
        map.put("projectId", "GCP项目ID，仅记录，不参与请求URL");
        map.put("location", "Vertex AI区域，仅记录，不参与请求URL");
        map.put("model", "完整Vertex模型资源名，如publishers/google/models/gemini-3.5-flash");
        map.put("gcsBucket", "GCS bucket名称");
        map.put("gcsEndpoint", "GCS JSON API endpoint，默认https://storage.googleapis.com");
        map.put("inputPrefix", "GCS输入文件前缀");
        map.put("outputPrefix", "GCS输出目录前缀");
        map.put("displayNamePrefix", "Vertex BatchPredictionJob displayName前缀");
        map.put("supportSystemInstruction", "是否支持系统指令");
        map.put("supportThinkConfig", "是否支持开启思考");
        return map;
    }
}

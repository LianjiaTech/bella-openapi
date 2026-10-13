package com.ke.bella.openapi.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelCapacity implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String TYPE_LLM = "llm";
    public static final String TYPE_REALTIME = "realtime";
    public static final String TYPE_QPS = "qps";

    private String type;
    private Long rpm;
    private Long tpm;
    private Long parallelLimit;
    private Long qps;
}

package com.ke.bella.openapi.protocol.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private Long created;
    @Builder.Default
    private String object = "model";

    @JsonProperty("owned_by")
    private String ownedBy;
}
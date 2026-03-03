package com.ke.bella.openapi.protocol.model;

import com.ke.bella.openapi.protocol.OpenapiListResponse;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ModelListResponse extends OpenapiListResponse<ModelInfo> {

    public ModelListResponse(List<ModelInfo> data) {
        super(data, "list", null, false);
    }
}
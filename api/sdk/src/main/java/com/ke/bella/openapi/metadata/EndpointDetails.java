package com.ke.bella.openapi.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.EnumDto;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EndpointDetails implements Serializable {
    private static final long serialVersionUID = 1L;
    private String endpoint;
    private List<Model> models;
    private List<EnumDto> features;
    private PriceDetails priceDetails;
}

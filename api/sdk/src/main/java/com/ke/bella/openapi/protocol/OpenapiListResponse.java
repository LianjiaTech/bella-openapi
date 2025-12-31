package com.ke.bella.openapi.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenapiListResponse<T> extends OpenapiResponse {
    private List<T> data;
    private String object = "list";
    @JsonProperty("last_id")
    private String lastId;
    @JsonProperty("has_more")
    private Boolean hasMore = false;
}

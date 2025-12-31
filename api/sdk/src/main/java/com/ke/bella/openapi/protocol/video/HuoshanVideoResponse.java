package com.ke.bella.openapi.protocol.video;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HuoshanVideoResponse {

    private String id;

    private Integer code;

    private String message;
}

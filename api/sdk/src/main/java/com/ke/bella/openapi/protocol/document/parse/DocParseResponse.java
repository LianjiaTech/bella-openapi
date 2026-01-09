package com.ke.bella.openapi.protocol.document.parse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class DocParseResponse {
    private DocParseResult result;
    private String status; // success, failed, processing
    private String message;
    @JsonIgnore
    private String token;
    @JsonIgnore
    private Runnable callback;
}

package com.ke.bella.openapi.protocol.video;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoUsage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Integer completion_tokens;
    
    private Integer prompt_tokens;
    
    private Integer total_tokens;
}

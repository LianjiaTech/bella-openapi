package com.ke.bella.openapi.protocol.video;

import java.io.Serializable;

import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.ke.bella.openapi.protocol.UserRequest;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class VideoRemixRequest implements UserRequest, Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank
    private String prompt;

    private String user;
}

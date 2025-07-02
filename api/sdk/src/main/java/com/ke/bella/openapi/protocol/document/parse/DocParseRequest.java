package com.ke.bella.openapi.protocol.document.parse;

import com.ke.bella.openapi.protocol.UserRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocParseRequest implements UserRequest {
    @NotNull
    private SourceFile file;
    private String user;
    private String model;
    private String type; //task, block
}

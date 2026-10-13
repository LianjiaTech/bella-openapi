package com.ke.bella.openapi.protocol.tts;

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
public class HuoShanV3Response implements Serializable {
    private int code;
    private String message;
    private String data;
    private Object sentence;
    private Object usage;

    public static final int CODE_OK = 0;
    public static final int CODE_SESSION_FINISH = 20000000;
    public static final int CODE_TEXT_LIMIT = 40402003;
    public static final int CODE_PERMISSION = 45000000;
    public static final int CODE_SERVER_ERROR = 55000000;

    public boolean isSuccess() {
        return code == CODE_OK || code == CODE_SESSION_FINISH;
    }
}

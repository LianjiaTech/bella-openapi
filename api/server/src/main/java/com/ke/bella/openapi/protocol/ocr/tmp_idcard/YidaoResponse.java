package com.ke.bella.openapi.protocol.ocr.tmp_idcard;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.ocr.YidaoBaseResponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class YidaoResponse extends YidaoBaseResponse<YidaoResponse.Result> {
    private static final long serialVersionUID = 1L;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result implements Serializable {
        private static final long serialVersionUID = 1L;

        private FieldData name;
        private FieldData gender;
        private FieldData nationality;
        private FieldData birthdate;
        private FieldData address;
        private FieldData idno;
        private FieldData issued;
        private FieldData valid;
    }
}

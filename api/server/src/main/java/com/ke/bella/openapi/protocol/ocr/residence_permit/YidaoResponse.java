package com.ke.bella.openapi.protocol.ocr.residence_permit;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.ocr.provider.yidao.YidaoBaseResponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode(callSuper = true)
public class YidaoResponse extends YidaoBaseResponse<YidaoResponse.ResultData> {
    private static final long serialVersionUID = 1L;

    @JsonProperty("version")
    private String version;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResultData implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("name")
        private FieldData name;

        @JsonProperty("gender")
        private FieldData gender;

        @JsonProperty("pass_no")
        private FieldData passNo;

        @JsonProperty("idno")
        private FieldData idno;

        @JsonProperty("address")
        private FieldData address;

        @JsonProperty("birthdate")
        private FieldData birthdate;

        @JsonProperty("valid")
        private FieldData valid;

        @JsonProperty("issued")
        private FieldData issued;

        @JsonProperty("nationality")
        private FieldData nationality;

        @JsonProperty("issued_times")
        private FieldData issuedTimes;
    }

}

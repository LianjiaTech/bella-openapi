package com.ke.bella.openapi.protocol.ocr.hmt_travel_permit;

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

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResultData implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("chinese_name") // 正面，中文姓名
        private FieldData chineseName;

        @JsonProperty("english_name")
        private FieldData englishName;

        @JsonProperty("birthdate")
        private FieldData birthdate;

        @JsonProperty("gender")
        private FieldData gender;

        @JsonProperty("valid")
        private FieldData valid;

        @JsonProperty("authority")
        private FieldData authority;

        @JsonProperty("idno")
        private FieldData idno;

        @JsonProperty("issued_times")
        private FieldData issuedTimes;

        @JsonProperty("area")
        private FieldData area;

        @JsonProperty("name") // 背面，身份证姓名
        private FieldData name;

        @JsonProperty("MRZCode")
        private FieldData mrzCode;

        @JsonProperty("idno2")
        private FieldData idno2;
    }
}

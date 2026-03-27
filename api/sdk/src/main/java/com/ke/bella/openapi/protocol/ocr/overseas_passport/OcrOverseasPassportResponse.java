package com.ke.bella.openapi.protocol.ocr.overseas_passport;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.OpenapiResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrOverseasPassportResponse extends OpenapiResponse {
    private static final long serialVersionUID = 1L;
    private String request_id;
    private Object data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OverseasPassportData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String passport_type;       // 护照类型
        private String passport_no;         // 护照编号
        private String name;                // 姓名
        private String sex;                 // 性别
        private String birth_date;          // 出生日期
        private String nationality;         // 国籍
        private String nationality_code;    // 国家码
        private String issue_country;       // 签发国家
        private String valid_date_end;      // 有效期
        private String birth_place;         // 出生地
        private String mrz;                 // MRZ码
    }

}

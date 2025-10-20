package com.ke.bella.openapi.protocol.ocr.tmp_idcard;

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
public class OcrTmpIdcardResponse extends OpenapiResponse {
    private static final long serialVersionUID = 1L;
    private String request_id;
    private Object data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TmpIdcardData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;                // 姓名
        private String sex;                 // 性别
        private String nationality;         // 民族
        private String birth_date;          // 出生日期
        private String address;             // 地址
        private String issue_authority;     // 签发机关
        private String valid_date_start;    // 有效期开始
        private String valid_date_end;      // 有效期结束
        private String idcard_number;       // 身份证号
    }

}

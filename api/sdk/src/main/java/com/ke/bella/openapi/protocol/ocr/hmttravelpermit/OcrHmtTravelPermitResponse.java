package com.ke.bella.openapi.protocol.ocr.hmttravelpermit;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.OpenapiResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * OCR港澳台居民往来大陆/内地通行证识别响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@SuperBuilder
public class OcrHmtTravelPermitResponse extends OpenapiResponse {
    private static final long serialVersionUID = 1L;

    private String request_id;          // 请求唯一标识
    private HmtTravelPermitData data;   // 识别结果数据

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HmtTravelPermitData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;              // 中文姓名
        private String name_en;           // 英文姓名
        private String birth_date;        // 出生日期
        private String sex;               // 性别
        private String valid_date_start;  // 有效期开始日期
        private String valid_date_end;    // 有效期结束日期
        private String issue_authority;   // 签发机关
        private String permit_number;     // 证件号码
        private String issue_times;       // 换证次数
        private String idcard_name;       // 身份证姓名
        private String idcard_number;     // 身份证号码
        private String mrz;               // MRZ码
    }
}

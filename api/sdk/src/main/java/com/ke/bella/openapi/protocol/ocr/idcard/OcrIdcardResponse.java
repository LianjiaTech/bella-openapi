package com.ke.bella.openapi.protocol.ocr.idcard;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ke.bella.openapi.protocol.OpenapiResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * OCR身份证识别响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@SuperBuilder
public class OcrIdcardResponse extends OpenapiResponse {
    private static final long serialVersionUID = 1L;

    private String request_id;              // 请求唯一标识
    private IdCardSide side;                // 身份证面类型
    private Object data;                    // 识别结果数据

    /**
     * 身份证面类型枚举
     */
    @Getter
    @AllArgsConstructor
    public enum IdCardSide {
        PORTRAIT("portrait", "人像面"),
        NATIONAL_EMBLEM("national_emblem", "国徽面");

        @JsonValue
        private final String code;
        private final String description;
    }

    // 人像面数据结构
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PortraitData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;                // 姓名
        private String sex;                 // 性别
        private String nationality;         // 民族
        private String birth_date;          // 出生日期
        private String address;             // 地址
        private String idcard_number;       // 身份证号
    }

    // 国徽面数据结构
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NationalEmblemData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String issue_authority;     // 签发机关
        private String valid_date_start;    // 有效期开始
        private String valid_date_end;      // 有效期结束
    }
}

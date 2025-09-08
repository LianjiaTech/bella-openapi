package com.ke.bella.openapi.protocol.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OCR身份证识别响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
public class OcrIdcardResponse extends OpenapiResponse {
    private String request_id;              // 请求唯一标识
    private String error_code;              // 错误码
    private String error_msg;               // 错误信息
    private String side;                    // 身份证面：portrait/national_emblem
    private Object data;                    // 识别结果数据

    // 人像面数据结构
    @Data
    public static class PortraitData {
        private String name;                // 姓名
        private String sex;                 // 性别
        private String nationality;         // 民族
        private String birth_date;          // 出生日期
        private String address;             // 地址
        private String idcard_number;       // 身份证号
    }

    // 国徽面数据结构
    @Data
    public static class NationalEmblemData {
        private String issue_authority;     // 签发机关
        private String valid_date_start;    // 有效期开始
        private String valid_date_end;      // 有效期结束
    }
}

package com.ke.bella.openapi.protocol.ocr.businesslicense;

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
 * OCR营业执照识别响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@SuperBuilder
public class OcrBusinessLicenseResponse extends OpenapiResponse {
    private static final long serialVersionUID = 1L;

    private String request_id;                  // 请求唯一标识
    private BusinessLicenseData data;           // 识别结果数据

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BusinessLicenseData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String unified_social_credit_code;  // 统一社会信用代码
        private String license_number;              // 证照编号
        private String name;                        // 名称
        private String entity_type;                 // 类型
        private String legal_representative;        // 法定代表人
        private String business_scope;              // 经营范围
        private String registered_capital;          // 注册资本
        private String paid_in_capital;             // 实收资本（可选）
        private String establishment_date;          // 成立日期（格式：yyyy年MM月dd日）
        private String business_term_start;         // 营业期限开始日期（格式：yyyy年MM月dd日）
        private String business_term_end;           // 营业期限结束日期（格式：yyyy年MM月dd日，长期则为"长期"）
        private String address;                     // 住所
        private String issue_date;                  // 颁发日期（格式：yyyy年MM月dd日）
        private String issue_authority;             // 登记机关
        private String taxpayer_id;                 // 税务登记号
        private String composition_form;            // 组成形式

    }
}

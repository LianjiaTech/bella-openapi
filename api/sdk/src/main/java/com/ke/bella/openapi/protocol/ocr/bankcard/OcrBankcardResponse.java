package com.ke.bella.openapi.protocol.ocr.bankcard;

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
 * OCR银行卡识别响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@SuperBuilder
public class OcrBankcardResponse extends OpenapiResponse {
    private static final long serialVersionUID = 1L;

    private String request_id;              // 请求唯一标识
    private BankcardData data;              // 识别结果数据

    // 银行卡数据结构
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BankcardData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String card_number;         // 银行卡号
        private String bank_name;           // 银行名称
        private String card_type;           // 卡类型（借记卡/信用卡）
        private String valid_date;          // 有效期
    }
}

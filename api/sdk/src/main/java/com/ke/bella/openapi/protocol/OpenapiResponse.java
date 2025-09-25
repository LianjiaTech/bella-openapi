package com.ke.bella.openapi.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class OpenapiResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 错误
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private OpenapiError error;

    /**
     * response中安全检查为warning的内容，适配原协议
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object sensitives;

    /**
     * request中安全检查为warning的内容，适配原协议
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object requestRiskData;

    public static OpenapiResponse errorResponse(OpenapiError error) {
        OpenapiResponse response = new OpenapiResponse();
        response.setError(error);
        return response;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class OpenapiError implements Serializable {

        /**
         * 安全检查结果为failed，返回error中包含检查结果
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Object sensitive;
        /**
         * code通常存在
         */
        private String code;
        /**
         * code通常存在
         */
        private Integer httpCode = 400;
        /**
         * message通常存在
         */
        private String message;
        /**
         * type 可能为空
         */
        private String type;
        /**
         * 可能为空
         */
        private String param;

        public OpenapiError(String type, String message, Integer httpCode, Object sensitive) {
            this.message = message;
            this.type = type;
            this.code = httpCode.toString();
            this.sensitive = sensitive;
        }

        public OpenapiError(String type, String message, Integer httpCode) {
            this.message = message;
            this.type = type;
            this.code = httpCode.toString();
            this.httpCode = httpCode;
        }
    }

    public boolean supportClone() {
        return false;
    }

}

package com.ke.bella.openapi.protocol.ocr.overseaspassport;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduBaseResponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 百度海外护照OCR API原始响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode(callSuper = true)
public class BaiduResponse extends BaiduBaseResponse {
    private static final long serialVersionUID = 1L;

    @JsonProperty("words_result")
    private Map<String, List<WordItem>> wordsResult;

    @JsonProperty("words_result_num")
    private Integer wordsResultNum;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WordItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private String word;
    }
}

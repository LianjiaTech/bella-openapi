package com.ke.bella.openapi.protocol.ocr.general;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduBaseResponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 百度通用文字识别OCR API响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class BaiduResponse extends BaiduBaseResponse {
    private static final long serialVersionUID = 1L;
    private Integer direction;              // 图像方向：-1未定义，0正向，1逆时针90度，2逆时针180度，3逆时针270度
    private Integer wordsResultNum;         // 识别结果数，表示words_result的元素个数
    private List<WordsResult> wordsResult;  // 识别结果数组
    private List<ParagraphsResult> paragraphsResult;  // 段落检测结果
    private Integer paragraphsResultNum;    // 识别结果数，表示paragraphs_result的元素个数
    private Integer language;               // 语种类型：-1未定义，0英文，1日文，2韩文，3中文
    private String pdfFileSize;             // 传入PDF文件的总页数
    private String ofdFileSize;             // 传入OFD文件的总页数

    /**
     * 识别结果单行数据
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WordsResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private String words;           // 识别结果字符串
        private Probability probability;  // 识别结果中每一行的置信度值
    }

    /**
     * 置信度信息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Probability implements Serializable {
        private static final long serialVersionUID = 1L;

        private Double average;         // 行置信度平均值
        private Double variance;        // 行置信度方差
        private Double min;             // 行置信度最小值
    }

    /**
     * 段落检测结果
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ParagraphsResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private List<Integer> wordsResultIdx;  // 一个段落包含的行序号
    }
}

package com.ke.bella.openapi.protocol.ocr.general;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduBaseRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 百度智能云千帆 PaddleOCR-VL 请求结构
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaddleOCRRequest extends BaiduBaseRequest {

    /**
     * 必选参数: 大模型ID，目前固定为 "paddleocr-vl-0.9b"
     */
    private String model;

    /**
     * 必选参数: 支持图像/PDF的URL或Base64
     * 通过 prepareFileField() 方法从 url/image 字段设置
     */
    private String file;

    /**
     * 文件类型
     * 0: PDF
     * 1: 图像
     * Base64 上传时必传
     */
    private Integer fileType;

    /**
     * 图片方向自动矫正
     */
    private Boolean useDocOrientationClassify;

    /**
     * 切边展平/去畸变
     */
    private Boolean useDocUnwarping;

    /**
     * 开启版面分析（检测标题、段落等）
     */
    private Boolean useLayoutDetection;

    /**
     * 版面分析结果重叠过滤
     */
    private Boolean layoutNms;

    /**
     * 是否开启图表/表格识别
     */
    private Boolean useChartRecognition;

    /**
     * 重复惩罚系数，取值范围 [1.0, 2.0]
     */
    private Float repetitionPenalty;

    /**
     * 采样随机性，取值范围 [0, 2]
     */
    private Float temperature;

    /**
     * 核采样阈值，取值范围 [0, 1]
     */
    private Float topP;

    /**
     * 最小分辨率限制
     */
    private Integer minPixels;

    /**
     * 最大分辨率限制
     */
    private Integer maxPixels;

    /**
     * 是否返回可视化中间结果图
     */
    private Boolean visualize;

    /**
     * 重写清理方法，清理 file 字段而不是 image 字段
     */
    @Override
    public void clearLargeData() {
        if (!isCleared()) {
            this.file = null;
            super.clearLargeData();
        }
    }
}

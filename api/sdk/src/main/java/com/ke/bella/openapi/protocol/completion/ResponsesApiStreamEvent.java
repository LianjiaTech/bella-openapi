package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Responses API 流式事件数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsesApiStreamEvent {

    /**
     * 事件类型
     */
    private String type;

    /**
     * 序列号
     */
    private Integer sequence_number;

    /**
     * 增量内容
     */
    private String delta;

    /**
     * 输出项ID
     */
    private String item_id;

    /**
     * 输出索引
     */
    private Integer output_index;

    /**
     * 内容索引
     */
    private Integer content_index;

    /**
     * 摘要索引
     */
    private Integer summary_index;

    /**
     * 响应对象
     */
    private ResponsesApiResponse response;

    /**
     * 输出项对象
     */
    private ResponsesApiResponse.OutputItem item;
}

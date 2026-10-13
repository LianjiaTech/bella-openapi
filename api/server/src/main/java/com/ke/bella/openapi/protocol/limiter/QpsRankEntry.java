package com.ke.bella.openapi.protocol.limiter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QPS 排行榜条目
 * 注意：qps 为近似值，记录的是最后一次请求时的瞬时 QPS，非精确实时数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QpsRankEntry {
    /**
     * API Key 编码
     */
    private String akCode;

    /**
     * QPS 值（近似值）
     */
    private Long qps;
}

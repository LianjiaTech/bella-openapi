package com.ke.bella.openapi.protocol.limiter.manager;

import com.ke.bella.openapi.protocol.limiter.QpsCheckResult;
import com.ke.bella.openapi.protocol.limiter.QpsRankEntry;

import java.util.List;

/**
 * QPS 限流管理器接口 基于分段滑动窗口实现 APIKey 维度的 QPS 限流
 */
public interface QpsLimiterManager {

    /**
     * 检查 QPS 限制
     *
     * @param akCode   API Key 编码
     * @param qpsLimit APIKey 配置的 QPS 限制值，null 或 0 使用默认值，负数表示不限制
     *
     * @return 检查结果
     */
    QpsCheckResult checkLimit(String akCode, Integer qpsLimit);

    /**
     * 获取当前 QPS（近似值）
     */
    Long getCurrentQps(String akCode);

    /**
     * 获取 QPS Top N 排行榜
     */
    List<QpsRankEntry> getTopN(int topN);

    /**
     * 是否可用
     */

    boolean isEnabled();
}

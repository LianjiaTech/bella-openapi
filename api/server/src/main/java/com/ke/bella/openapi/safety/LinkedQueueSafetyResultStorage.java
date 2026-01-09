package com.ke.bella.openapi.safety;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 安全检查结果存储实现
 * 负责存储请求和响应的风险数据
 */
public class LinkedQueueSafetyResultStorage implements ISafetyResultStorage {
    private final ConcurrentLinkedQueue<Object> requestRiskDataQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<Object> responseRiskDataQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void addRiskData(Object riskData, boolean isRequest) {
        if(riskData != null) {
            if(isRequest) {
                requestRiskDataQueue.offer(riskData);
            } else {
                responseRiskDataQueue.offer(riskData);
            }
        }
    }

    @Override
    public Object getRequestRiskData() {
        return requestRiskDataQueue.poll();
    }

    @Override
    public Object getResponseRiskData() {
        return responseRiskDataQueue.poll();
    }
}

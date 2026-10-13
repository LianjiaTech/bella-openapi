package com.ke.bella.openapi.protocol.document.parse;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface DocParseAdaptor<T extends DocParseProperty> extends IProtocolAdaptor {

    default Object parse(DocParseRequest request, String channelCode, String url, T property, DocParseCallback callback) {
        DocParseTaskInfo taskInfo = doParse(request, channelCode, url, property);
        String[] infos = TaskIdUtils.extractTaskId(taskInfo.getTaskId());
        String taskId = infos[1];

        if("blocking".equals(request.getType())) {
            return waitForCompletion(taskId, url, property, request.getMaxTimeoutMillis());
        } else {
            if(StringUtils.isNotBlank(request.getCallbackUrl())) {
                callback.addCallbackTask(getClass().getSimpleName(), taskId, request.getCallbackUrl(), url, JacksonUtils.serialize(property));
            }
            return taskInfo;
        }
    }

    DocParseTaskInfo doParse(DocParseRequest request, String channelCode, String url, T property);

    DocParseResponse queryResult(String taskId, String url, T property);

    boolean isCompletion(String taskId, String url, T property);

    /**
     * 等待任务完成（阻塞模式）
     * 
     * @param taskId           任务ID
     * @param url              服务URL
     * @param property         渠道属性
     * @param maxTimeoutMillis 最大超时时间（毫秒）
     * 
     * @return 任务完成后的结果或超时异常
     */
    default Object waitForCompletion(String taskId, String url, T property, int maxTimeoutMillis) {
        // 最大等待时间，不能小于30s
        maxTimeoutMillis = Math.max(30000, maxTimeoutMillis);
        Logger log = LoggerFactory.getLogger(this.getClass());
        long startTime = System.currentTimeMillis();
        long timeoutTime = startTime + maxTimeoutMillis;

        log.info("Starting block mode waiting for task completion - taskId: {}, timeout: {}ms", taskId, maxTimeoutMillis);

        while (System.currentTimeMillis() < timeoutTime) {
            try {
                Thread.sleep(5000);
                // 检查任务是否完成
                if(isCompletion(taskId, url, property)) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.info("Task completed in block mode - taskId: {}, elapsed time: {}ms", taskId, elapsedTime);
                    DocParseResponse response = queryResult(taskId, url, property);
                    if(response.getCallback() != null) {
                        TaskExecutor.submit(response.getCallback());
                    }
                    return response;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Task waiting was interrupted - taskId: {}", taskId, e);
                throw BellaException.fromResponse(500, "Task waiting was interrupted for taskId: " + taskId);
            } catch (Exception e) {
                log.warn("Error during task completion check - taskId: {}, error: {}", taskId, e.getMessage());
            }
        }

        // 超时处理
        long elapsedTime = System.currentTimeMillis() - startTime;
        log.error("Task completion timeout in block mode - taskId: {}, elapsed time: {}ms", taskId, elapsedTime);
        throw BellaException.fromResponse(408, "Task completion timeout after " + maxTimeoutMillis + "ms for taskId: " + taskId);
    }

    default String endpoint() {
        return "/v1/document/parse";
    }
}

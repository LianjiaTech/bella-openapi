package com.ke.bella.openapi.protocol.document.parse;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.task.CallbackTaskService;
import com.ke.bella.openapi.task.TaskCompletionCallback;
import com.ke.bella.openapi.task.TaskData;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析回调服务
 * 基于CallbackTaskService实现，用于在文档解析完成后回调指定URL
 */
@Slf4j
@Service
public class DocParseCallbackService extends CallbackTaskService<DocParseCallbackService.DocParseCallbackTaskData> implements DocParseCallback {

    private static final String CALLBACK_ZSET_KEY = "doc:parse:callback:zset";

    @Autowired
    private AdaptorManager adaptorManager;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    /**
     * 添加文档解析回调任务
     * 
     * @param protocol        协议名称
     * @param taskId          任务ID
     * @param callbackUrl     回调地址
     * @param url             渠道URL
     * @param channelProperty 渠道属性（JSON字符串）
     */
    @Override
    public void addCallbackTask(String protocol, String taskId, String callbackUrl, String url, String channelProperty) {
        DocParseCallbackTaskData taskData = new DocParseCallbackTaskData(
                protocol, taskId, callbackUrl, url, channelProperty, 0, 0);
        addTask(taskData);
        log.info("Added doc parse callback task - protocol: {}, taskId: {}, callbackUrl: {}",
                protocol, taskId, callbackUrl);
    }

    @Override
    protected String getZSetKey() {
        return CALLBACK_ZSET_KEY;
    }

    @Override
    protected Class<DocParseCallbackTaskData> getTaskDataClass() {
        return DocParseCallbackTaskData.class;
    }

    @Override
    protected TaskCompletionCallback<DocParseCallbackTaskData> getTaskCompletionCallback() {
        return new DocParseCallbackHandler();
    }

    /**
     * 文档解析回调任务数据
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DocParseCallbackTaskData implements TaskData {
        private String protocol;
        private String taskId;
        private String callbackUrl;
        private String url;
        private String channelProperty; // JSON字符串形式的属性
        private long timestamp;
        private int remainingRetries;

        @Override
        public String getTaskId() {
            return taskId;
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj)
                return true;
            if(obj == null || getClass() != obj.getClass())
                return false;
            DocParseCallbackTaskData task = (DocParseCallbackTaskData) obj;
            return Objects.equals(taskId, task.taskId) && Objects.equals(protocol, task.protocol);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, protocol);
        }
    }

    /**
     * 文档解析回调处理器
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private class DocParseCallbackHandler implements TaskCompletionCallback<DocParseCallbackTaskData> {

        @Override
        public boolean isTaskCompleted(DocParseCallbackTaskData taskData) throws Exception {
            try {
                // 获取协议对应的适配器
                DocParseAdaptor adaptor = adaptorManager.getProtocolAdaptor(
                        "/v1/document/parse", taskData.getProtocol(), DocParseAdaptor.class);

                if(adaptor == null) {
                    log.error("No adaptor found for protocol: {}", taskData.getProtocol());
                    return true; // 没有适配器，认为任务完成（避免无限重试）
                }

                // 解析渠道属性
                DocParseProperty property = (DocParseProperty) JacksonUtils.deserialize(taskData.getChannelProperty(), adaptor.getPropertyClass());

                // 使用适配器判断任务是否完成
                return adaptor.isCompletion(taskData.getTaskId(), taskData.getUrl(), property);

            } catch (Exception e) {
                log.error("Error checking task completion for taskId: {}, protocol: {}, error: {}",
                        taskData.getTaskId(), taskData.getProtocol(), e.getMessage(), e);
                throw e;
            }
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public boolean onTaskCompleted(DocParseCallbackTaskData taskData) throws Exception {
            try {
                // 获取协议对应的适配器
                DocParseAdaptor adaptor = adaptorManager.getProtocolAdaptor(
                        "/v1/document/parse", taskData.getProtocol(), DocParseAdaptor.class);

                if(adaptor == null) {
                    log.error("No adaptor found for protocol: {} when processing callback",
                            taskData.getProtocol());
                    return true; // 没有适配器，认为处理完成
                }

                // 解析渠道属性
                DocParseProperty property = (DocParseProperty) JacksonUtils.deserialize(taskData.getChannelProperty(), adaptor.getPropertyClass());

                // 查询任务结果
                DocParseResponse response = adaptor.queryResult(
                        taskData.getTaskId(), taskData.getUrl(), property);

                // 发送HTTP回调请求
                boolean callbackSuccess = sendCallback(taskData.getCallbackUrl(), response);

                if(callbackSuccess) {
                    log.info("Successfully sent callback for taskId: {}, protocol: {}, callbackUrl: {}",
                            taskData.getTaskId(), taskData.getProtocol(), taskData.getCallbackUrl());
                    if(response.getCallback() != null) {
                        TaskExecutor.submit(response.getCallback());
                    }
                } else {
                    log.warn("Failed to send callback for taskId: {}, protocol: {}, callbackUrl: {}",
                            taskData.getTaskId(), taskData.getProtocol(), taskData.getCallbackUrl());
                }

                return callbackSuccess;

            } catch (Exception e) {
                log.error("Error processing callback for taskId: {}, protocol: {}, error: {}",
                        taskData.getTaskId(), taskData.getProtocol(), e.getMessage(), e);
                throw e;
            }
        }

        /**
         * 发送HTTP回调请求
         */
        private boolean sendCallback(String callbackUrl, DocParseResponse response) {
            try {
                String responseJson = JacksonUtils.serialize(response);

                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json"), responseJson);

                Request request = new Request.Builder()
                        .url(callbackUrl)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("User-Agent", "Bella-OpenAPI-Callback/1.0")
                        .build();

                try (Response httpResponse = httpClient.newCall(request).execute()) {
                    boolean isSuccess = httpResponse.isSuccessful();

                    log.debug("Callback response - URL: {}, Status: {}, Success: {}",
                            callbackUrl, httpResponse.code(), isSuccess);

                    return isSuccess;
                }

            } catch (IOException e) {
                log.error("Failed to send callback to URL: {}, error: {}", callbackUrl, e.getMessage(), e);
                return false;
            } catch (Exception e) {
                log.error("Unexpected error when sending callback to URL: {}, error: {}",
                        callbackUrl, e.getMessage(), e);
                return false;
            }
        }
    }
}

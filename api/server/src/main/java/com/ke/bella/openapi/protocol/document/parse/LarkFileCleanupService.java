package com.ke.bella.openapi.protocol.document.parse;

import com.ke.bella.openapi.task.CallbackTaskService;
import com.ke.bella.openapi.task.TaskCompletionCallback;
import com.ke.bella.openapi.task.TaskData;
import com.lark.oapi.Client;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.ke.bella.openapi.protocol.document.parse.LarkClientUtils.deleteFile;
import static com.ke.bella.openapi.protocol.document.parse.LarkClientUtils.queryTaskResult;

/**
 * Lark文件清理服务
 * 基于CallbackTaskService实现的定时任务，定期检查任务状态并清理已完成任务的文件
 */
@Slf4j
@Service
public class LarkFileCleanupService extends CallbackTaskService<LarkFileCleanupService.LarkCleanupTaskData> {

    private static final String CLEANUP_ZSET_KEY = "lark:file:cleanup:zset";

    /**
     * 添加清理任务
     * 
     * @param fileToken 文件token
     * @param ticket    任务ticket
     * @param property  Lark配置属性
     */
    public void addCleanupTask(String fileToken, String ticket, LarkProperty property) {
        LarkCleanupTaskData task = new LarkCleanupTaskData(fileToken, ticket, "file",
                property.getClientId(), property.getClientSecret(), 0, 0);
        addTask(task);
        log.info("Added cleanup task for file: {}, ticket: {}", fileToken, ticket);
    }

    @Override
    protected String getZSetKey() {
        return CLEANUP_ZSET_KEY;
    }

    @Override
    protected Class<LarkCleanupTaskData> getTaskDataClass() {
        return LarkCleanupTaskData.class;
    }

    @Override
    protected TaskCompletionCallback<LarkCleanupTaskData> getTaskCompletionCallback() {
        return new LarkFileCleanupCallback();
    }

    /**
     * Lark清理任务数据结构
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LarkCleanupTaskData implements TaskData {
        private String fileToken;
        private String ticket;
        private String fileType;
        private String clientId;
        private String clientSecret;
        private long timestamp;
        private int remainingRetries;

        @Override
        public String getTaskId() {
            return ticket;
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj)
                return true;
            if(obj == null || getClass() != obj.getClass())
                return false;
            LarkCleanupTaskData task = (LarkCleanupTaskData) obj;
            return Objects.equals(ticket, task.ticket);
        }

        @Override
        public int hashCode() {
            return ticket != null ? ticket.hashCode() : 0;
        }
    }

    /**
     * Lark文件清理回调处理器
     */
    private static class LarkFileCleanupCallback implements TaskCompletionCallback<LarkCleanupTaskData> {

        private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LarkFileCleanupCallback.class);

        @Override
        public boolean isTaskCompleted(LarkCleanupTaskData taskData) {
            Client client = LarkClientProvider.client(taskData.getClientId(), taskData.getClientSecret());
            DocParseResponse response = queryTaskResult(client, taskData.getTicket());
            return "success".equals(response.getStatus()) || "failed".equals(response.getStatus());
        }

        @Override
        public boolean onTaskCompleted(LarkCleanupTaskData taskData) {
            Client client = LarkClientProvider.client(taskData.getClientId(), taskData.getClientSecret());
            boolean deleted = deleteFile(client, taskData.getFileToken(), taskData.getFileType());
            if(deleted) {
                logger.info("Successfully cleaned up file for completed task - fileToken: {}, ticket: {}",
                        taskData.getFileToken(), taskData.getTicket());
            } else {
                logger.warn("Failed to delete file for completed task - fileToken: {}, ticket: {}",
                        taskData.getFileToken(), taskData.getTicket());
            }
            return deleted;
        }
    }
}

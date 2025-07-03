package com.ke.bella.openapi.protocol.document.parse;

import com.ke.bella.openapi.utils.JacksonUtils;
import com.lark.oapi.Client;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.ke.bella.openapi.protocol.document.parse.LarkClientUtils.deleteFile;
import static com.ke.bella.openapi.protocol.document.parse.LarkClientUtils.queryTaskResult;

/**
 * Lark文件清理服务
 * 简单的定时任务，定期检查任务状态并清理已完成任务的文件
 */
@Slf4j
@Service
public class LarkFileCleanupService {

    private static final String CLEANUP_ZSET_KEY = "lark:file:cleanup:zset";
    
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 添加清理任务
     * @param fileToken 文件token
     * @param ticket 任务ticket
     * @param property Lark配置属性
     */
    public void addCleanupTask(String fileToken, String ticket, LarkProperty property) {
        long executeTime = System.currentTimeMillis() + 30000;
        CleanupTask task = new CleanupTask(fileToken, ticket, "file", property.getClientId(), property.getClientSecret(), executeTime);
        String taskJson = JacksonUtils.serialize(task);

        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(CLEANUP_ZSET_KEY);
        zset.add(executeTime, taskJson);

        LOGGER.info("Added cleanup task for file: {}, ticket: {}", fileToken, ticket);
    }

    /**
     * 定时清理任务，每分钟执行一次
     */
    @Scheduled(fixedRate = 10000) // 每10s执行一次
    public void cleanupCompletedTasks() {
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(CLEANUP_ZSET_KEY);
        
        long currentTime = System.currentTimeMillis();
        
        while (!zset.isEmpty()) {
            // 原子操作取出并删除最小score的任务
            String taskJson = zset.pollFirst();
            if (taskJson == null) {
                break; // 队列为空
            }
            
            try {
                CleanupTask task = JacksonUtils.deserialize(taskJson, CleanupTask.class);

                if(task == null) {
                    continue;
                }

                // 检查任务执行时间是否到了
                if (task.getTimestamp() > currentTime) {
                    // 任务还没到执行时间，重新放回去并退出
                    zset.add(task.getTimestamp(), taskJson);
                    break;
                }
                
                // 使用ticket查询任务状态
                Client client = LarkClientProvider.client(task.getClientId(), task.getClientSecret());
                DocParseResponse response = queryTaskResult(client, task.getTicket());
                
                if ("success".equals(response.getStatus()) || "failed".equals(response.getStatus())) {
                    // 任务已完成（成功或失败），删除文件
                    boolean deleted = deleteFile(client, task.getFileToken(), task.getFileType());
                    if (deleted) {
                        // 任务已成功处理
                        LOGGER.info("Successfully cleaned up file for completed task - fileToken: {}, ticket: {}",
                                task.getFileToken(), task.getTicket());
                    } else {
                        // 删除文件失败，10s后重试
                        task.setTimestamp(currentTime + 10000);
                        String newTaskJson = JacksonUtils.serialize(task);
                        zset.add(task.getTimestamp(), newTaskJson);
                        LOGGER.warn("Failed to delete file for completed task, will retry in 10s - fileToken: {}, ticket: {}",
                                task.getFileToken(), task.getTicket());
                    }
                } else {
                    // 任务仍在处理中，10s后重新检查
                    task.setTimestamp(currentTime + 10000);
                    String newTaskJson = JacksonUtils.serialize(task);
                    zset.add(task.getTimestamp(), newTaskJson);
                    LOGGER.debug("Task still processing, will retry in 10s - fileToken: {}, ticket: {}",
                            task.getFileToken(), task.getTicket());
                }
                
            } catch (Exception e) {
                // 查询失败，10s后重试
                CleanupTask task = JacksonUtils.deserialize(taskJson, CleanupTask.class);
                if(task != null) {
                    task.setTimestamp(currentTime + 10000);
                    String newTaskJson = JacksonUtils.serialize(task);
                    zset.add(task.getTimestamp(), newTaskJson);
                }
                LOGGER.error("Failed to query task status for cleanup, will retry in 10s - error: {}", e.getMessage());
            }
        }
    }

    /**
     * 清理任务数据结构
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class CleanupTask {
        private String fileToken;
        private String ticket;
        private String fileType;
        private String clientId;
        private String clientSecret;
        private long timestamp;
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CleanupTask task = (CleanupTask) obj;
            return Objects.equals(ticket, task.ticket);
        }
        
        @Override
        public int hashCode() {
            return ticket != null ? ticket.hashCode() : 0;
        }
    }
}

package com.ke.bella.openapi.protocol.document.parse;

import com.lark.oapi.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lark文件清理服务
 * 简单的定时任务，定期检查任务状态并清理已完成任务的文件
 */
@Slf4j
@Service
public class LarkFileCleanupService {

    /**
     * 等待清理的任务队列
     */
    private final ConcurrentLinkedQueue<CleanupTask> cleanupQueue = new ConcurrentLinkedQueue<>();

    /**
     * 添加清理任务
     * @param fileToken 文件token
     * @param ticket 任务ticket
     * @param fileType 文件类型
     * @param property Lark配置属性
     */
    public void addCleanupTask(String fileToken, String ticket, String fileType, LarkProperty property) {
        CleanupTask task = new CleanupTask(fileToken, ticket, fileType, property);
        cleanupQueue.offer(task);
        log.info("Added cleanup task for file: {}, ticket: {}", fileToken, ticket);
    }

    /**
     * 定时清理任务，每分钟执行一次
     */
    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    public void cleanupCompletedTasks() {
        log.debug("Starting cleanup task scan, queue size: {}", cleanupQueue.size());
        
        // 处理队列中的所有任务
        while (!cleanupQueue.isEmpty()) {
            CleanupTask task = cleanupQueue.poll();
            if (task == null) {
                break;
            }

            try {
                // 使用ticket查询任务状态
                Client client = LarkClientProvider.client(task.getProperty().getClientId(), task.getProperty().getClientSecret());
                DocParseResponse response = queryTaskResult(client, task.getTicket());
                
                if ("success".equals(response.getStatus()) || "failed".equals(response.getStatus())) {
                    // 任务已完成（成功或失败），删除文件
                    boolean deleted = LarkAdaptor.deleteFile(client, task.getFileToken(), task.getFileType());
                    if (deleted) {
                        log.info("Successfully cleaned up file for completed task - fileToken: {}, ticket: {}, status: {}", 
                                task.getFileToken(), task.getTicket(), response.getStatus());
                    } else {
                        log.warn("Failed to delete file for completed task - fileToken: {}, ticket: {}, status: {}", 
                                task.getFileToken(), task.getTicket(), response.getStatus());
                    }
                } else if ("processing".equals(response.getStatus())) {
                    // 任务仍在处理中，重新加入队列等待下次检查
                    cleanupQueue.offer(task);
                    log.debug("Task still processing, re-queued - fileToken: {}, ticket: {}", 
                            task.getFileToken(), task.getTicket());
                } else {
                    // 未知状态，记录日志但不删除文件
                    log.warn("Unknown task status for cleanup - fileToken: {}, ticket: {}, status: {}", 
                            task.getFileToken(), task.getTicket(), response.getStatus());
                }
                
            } catch (Exception e) {
                // 查询失败，重新加入队列等待下次重试
                cleanupQueue.offer(task);
                log.error("Failed to query task status for cleanup - fileToken: {}, ticket: {}, error: {}", 
                        task.getFileToken(), task.getTicket(), e.getMessage());
            }
        }
        
        log.debug("Cleanup task scan completed, remaining queue size: {}", cleanupQueue.size());
    }

    /**
     * 查询任务结果（复用LarkAdaptor的逻辑）
     */
    private DocParseResponse queryTaskResult(Client client, String ticket) {
        return LarkAdaptor.queryTaskResult(client, ticket);
    }

    /**
     * 清理任务数据结构
     */
    private static class CleanupTask {
        private final String fileToken;
        private final String ticket;
        private final String fileType;
        private final LarkProperty property;

        public CleanupTask(String fileToken, String ticket, String fileType, LarkProperty property) {
            this.fileToken = fileToken;
            this.ticket = ticket;
            this.fileType = fileType;
            this.property = property;
        }

        public String getFileToken() {
            return fileToken;
        }

        public String getTicket() {
            return ticket;
        }

        public String getFileType() {
            return fileType;
        }

        public LarkProperty getProperty() {
            return property;
        }
    }
}
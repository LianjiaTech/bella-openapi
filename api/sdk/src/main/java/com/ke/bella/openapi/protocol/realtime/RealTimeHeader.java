package com.ke.bella.openapi.protocol.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.io.Serializable;
import java.util.UUID;

/**
 * 实时语音识别消息头
 */
@Data
public class RealTimeHeader implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    @JsonProperty("message_id")
    private String messageId;
    @JsonProperty("task_id")
    private String taskId;
    private String namespace;
    private String appkey;
    private Integer status;
    @JsonProperty("status_message")
    private String statusMessage;

    public static RealTimeHeader header(RealTimeEventType event, String taskId) {
        RealTimeHeader header = new RealTimeHeader();
        header.setName(event.getValue());
        header.setMessageId(UUID.randomUUID().toString());
        header.setTaskId(taskId);
        header.setStatus(event.isSuccess() ? 20000000 : 50000000);
        header.setStatusMessage(event.isSuccess() ? "GATEWAY|SUCCESS|Success." : "FAIL");
        return header;
    }

    public static RealTimeHeader errorHeader(int status, String errorMessage, String taskId) {
        RealTimeHeader header = new RealTimeHeader();
        header.setName(RealTimeEventType.TASK_FAILED.getValue());
        header.setMessageId(UUID.randomUUID().toString());
        header.setTaskId(taskId != null ? taskId : "");
        header.setStatus(status);
        header.setStatusMessage(errorMessage);
        return header;
    }
}

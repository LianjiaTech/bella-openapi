package com.ke.bella.openapi.protocol.tts.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealtimeTtsHeader {
    private String name;
    @JsonProperty("message_id")
    private String messageId;
    @JsonProperty("session_id")
    private String sessionId;
    @JsonProperty("task_id")
    private String taskId;
    private Integer sequence;
    private Long timestamp;
    private Integer status;
    @JsonProperty("status_message")
    private String statusMessage;
    private Integer version;
}

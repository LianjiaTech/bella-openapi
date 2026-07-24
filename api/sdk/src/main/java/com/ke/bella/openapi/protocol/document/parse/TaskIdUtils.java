package com.ke.bella.openapi.protocol.document.parse;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TaskIdUtils {

    public String buildTaskId(String channelCode, String taskId) {
        return channelCode + "___" + taskId;
    }

    public String[] extractTaskId(String taskId) {
        String[] infos = taskId.split("___");
        if(infos.length != 2) {
            throw new IllegalStateException("invalid taskId");
        }
        return infos;
    }
}

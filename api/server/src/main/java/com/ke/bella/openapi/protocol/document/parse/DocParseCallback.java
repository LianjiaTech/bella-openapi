package com.ke.bella.openapi.protocol.document.parse;

public interface DocParseCallback {
    void addCallbackTask(String protocol, String taskId, String callbackUrl, String url, String channelProperty);
}

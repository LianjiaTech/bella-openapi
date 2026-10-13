package com.ke.bella.openapi.protocol.asr.realtime;

import org.apache.commons.lang3.StringUtils;

import com.ke.bella.openapi.EndpointProcessData;

import okhttp3.Response;

public final class AsrWebSocketHandshakeTrace {
    private AsrWebSocketHandshakeTrace() {
    }

    public static Values capture(Response response, EndpointProcessData processData) {
        String logId = null;
        String connectId = null;
        try {
            if(response != null) {
                logId = StringUtils.trimToNull(response.header("X-Tt-Logid"));
                connectId = StringUtils.trimToNull(response.header("X-Api-Connect-Id"));
                if(connectId == null) {
                    connectId = StringUtils.trimToNull(response.request().header("X-Api-Connect-Id"));
                }
            }
        } catch (Exception ignored) {
        }
        if(processData != null) {
            processData.setUpstreamLogId(logId);
            processData.setUpstreamConnectId(connectId);
            if(logId != null && StringUtils.isBlank(processData.getChannelRequestId())) {
                processData.setChannelRequestId(logId);
            }
        }
        return new Values(logId, connectId);
    }

    public static final class Values {
        private final String logId;
        private final String connectId;

        private Values(String logId, String connectId) {
            this.logId = logId;
            this.connectId = connectId;
        }

        public String getLogId() {
            return logId;
        }

        public String getConnectId() {
            return connectId;
        }
    }
}

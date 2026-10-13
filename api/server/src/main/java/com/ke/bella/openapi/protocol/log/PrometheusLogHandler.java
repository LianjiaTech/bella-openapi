package com.ke.bella.openapi.protocol.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.metrics.ChatCompletionPrometheusRecorder;
import com.ke.bella.openapi.protocol.metrics.TtsPrometheusRecorder;
import com.ke.bella.openapi.protocol.metrics.VoicePrometheusRecorder;
import com.lmax.disruptor.EventHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrometheusLogHandler implements EventHandler<LogEvent> {

    private final ChatCompletionPrometheusRecorder chatCompletionPrometheusRecorder;
    private final VoicePrometheusRecorder voicePrometheusRecorder;
    private final TtsPrometheusRecorder ttsPrometheusRecorder;

    public PrometheusLogHandler(ChatCompletionPrometheusRecorder chatCompletionPrometheusRecorder,
            VoicePrometheusRecorder voicePrometheusRecorder, TtsPrometheusRecorder ttsPrometheusRecorder) {
        this.chatCompletionPrometheusRecorder = chatCompletionPrometheusRecorder;
        this.voicePrometheusRecorder = voicePrometheusRecorder;
        this.ttsPrometheusRecorder = ttsPrometheusRecorder;
    }

    @Override
    public void onEvent(LogEvent event, long sequence, boolean endOfBatch) throws Exception {
        EndpointProcessData logData = event.getData();
        if(logData == null || !logData.isInnerLog() || event.isCostOnly()) {
            return;
        }
        try {
            if(logData.getResponseMillis() <= 0) {
                logData.setResponseMillis(System.currentTimeMillis());
            }
            chatCompletionPrometheusRecorder.finish(logData);
            voicePrometheusRecorder.finish(logData);
            ttsPrometheusRecorder.finish(logData);
        } catch (Exception e) {
            log.warn("record endpoint prometheus metrics failed, requestId={}", logData.getRequestId(), e);
        }
    }
}

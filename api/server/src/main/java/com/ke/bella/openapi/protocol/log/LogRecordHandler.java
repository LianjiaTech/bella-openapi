package com.ke.bella.openapi.protocol.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.db.log.LogRepo;
import com.lmax.disruptor.EventHandler;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
public class LogRecordHandler implements EventHandler<LogEvent> {
    private final List<LogRepo> logRepos;

    public LogRecordHandler(List<LogRepo> logRepos) {
        this.logRepos = logRepos;
    }

    @Override
    public void onEvent(LogEvent event, long sequence, boolean endOfBatch) throws Exception {
        logResponseReturn(event);
        logRepos.forEach(logRepo -> logRepo.record(event.getData()));
    }

    private void logResponseReturn(LogEvent event) {
        EndpointProcessData data = event.getData();
        if(data == null) {
            return;
        }
        try {
            Map<String, Object> metrics = data.getMetrics();
            Integer inputToken = metric(metrics, "input_token");
            Integer outputToken = metric(metrics, "output_token");
            Integer totalToken = metric(metrics, "total_token");
            totalToken = totalToken == null ? metric(metrics, "token") : totalToken;
            if(totalToken == null && (inputToken != null || outputToken != null)) {
                totalToken = (inputToken == null ? 0 : inputToken) + (outputToken == null ? 0 : outputToken);
            }
            Long elapsedMillis = data.getResponseMillis() > 0 && data.getRequestMillis() > 0 ? data.getResponseMillis() - data.getRequestMillis() : null;
            OpenapiResponse.OpenapiError error = data.getResponse() == null ? null : data.getResponse().getError();
            log.info("[nodeType=OPENAPI_RESPONSE_RETURN][traceId={}][requestId={}] endpoint={} model={} akCode={} channelCode={} protocol={} supplier={} deployName={} channelRequestId={} status={} errorCode={} errorType={} errorParam={} errorMessage={} failureStage={} responseMillis={} elapsedMillis={} duration={} firstPackageTime={} cost={} costDetails={} billingSkipped={} priceInfoPresent={} usagePresent={} inputToken={} outputToken={} totalToken={} forwardHost={} forwardPath={} innerLog={} batch={} nativeSend={} costOnly={}",
                    data.getBellaTraceId(), data.getRequestId(), data.getEndpoint(), data.getModel(), data.getAkCode(), data.getChannelCode(),
                    data.getProtocol(), data.getSupplier(), data.getDeployName(), data.getChannelRequestId(),
                    error == null ? 200 : error.getHttpCode(), error == null ? null : error.getCode(), error == null ? null : error.getType(),
                    error == null ? null : error.getParam(), error == null ? null : error.getMessage(), data.getFailureStage(),
                    data.getResponseMillis(), elapsedMillis, data.getDuration(), data.getFirstPackageTime(), data.getCost(), data.getCostDetails(), data.isBillingSkipped(),
                    StringUtils.isNotBlank(data.getPriceInfo()), data.getUsage() != null, inputToken, outputToken, totalToken,
                    data.getForwardHost(), data.getForwardPath(), data.isInnerLog(), data.isBatch(), data.isNativeSend(), event.isCostOnly());
        } catch (Exception e) {
            log.warn("log response monitor failed, requestId={}", data.getRequestId(), e);
        }
    }

    private Integer metric(Map<String, Object> metrics, String key) {
        Object value = metrics == null ? null : metrics.get(key);
        if(value instanceof Number) {
            return ((Number) value).intValue();
        }
        if(value instanceof String && StringUtils.isNumeric((String) value)) {
            return Integer.parseInt((String) value);
        }
        return null;
    }

    @Data
    @Builder
    public static class RecordLogInfo {
        private EndpointProcessData log;
        private String repositoryCode;
    }
}

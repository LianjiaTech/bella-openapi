package com.ke.bella.openapi.protocol.completion;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.metrics.MetricsResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class CompletionMetricsResolver implements MetricsResolver {
    private static final Pattern timePattern = Pattern.compile("(?<time>\\d+)\\s+(?<unit>milliseconds|seconds)");

    private boolean canResolve(OpenapiResponse response, String supplier) {
        return response.getError() != null && response.getError().getHttpCode() == 429 && supplier.equals("Azure");
    }

    @Override
    public Integer resolveUnavailableSeconds(EndpointProcessData processData) {
        OpenapiResponse response = processData.getResponse();
        int seconds = 1;
        if(canResolve(response, processData.getSupplier())) {
            String msg = response.getError().getMessage();
            Matcher timeMatcher = timePattern.matcher(msg);
            if(timeMatcher.find()) {
                int time = Integer.parseInt(timeMatcher.group("time"));
                String unit = timeMatcher.group("unit");
                switch (unit) {
                case "milliseconds":
                    seconds = time / 1000;
                    break;
                case "seconds":
                    seconds = time;
                    break;
                default:
                    break;
                }
                // 防止穿透惩罚，多禁1s
                seconds += 1;
            }
        }
        return seconds;
    }

    @Override
    public List<String> metricsName() {
        return Lists.newArrayList("ttft", "ttlt", "input_token", "output_token");
    }

    @Override
    public String support() {
        return "/v*/chat/completions";
    }
}

package com.ke.bella.openapi;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class EndpointContext {
    private static final ThreadLocal<EndpointProcessData> endpointRequestInfo = new ThreadLocal<>();

    private static final ThreadLocal<HttpServletRequest> requestCache = new ThreadLocal<>();

    private static final ThreadLocal<Boolean> isLastRequest = new ThreadLocal<>();

    private static final ThreadLocal<Integer> requestSize = new ThreadLocal<>();

    public static EndpointProcessData getProcessData() {
        if(endpointRequestInfo.get() == null) {
            EndpointProcessData endpointProcessData = new EndpointProcessData();
            endpointProcessData.setInnerLog(true);
            endpointProcessData.setBellaTraceId(BellaContext.getTraceId());
            endpointProcessData.setRequestId(BellaContext.getRequestId());
            endpointProcessData.setMock(BellaContext.isMock());
            endpointRequestInfo.set(endpointProcessData);
        }
        return endpointRequestInfo.get();
    }

    public static void setProcessData(EndpointProcessData processData) {
        endpointRequestInfo.set(processData);
    }

    public static HttpServletRequest getRequest() {
        Assert.notNull(requestCache.get(), "requestCache is empty");
        return requestCache.get();
    }

    public static HttpServletRequest getRequestIgnoreNull() {
        return requestCache.get();
    }

    public static void setRequest(HttpServletRequest request) {
        requestCache.set(request);

        // 从原始请求获取大小
        int contentLength = request.getContentLength();
        if(contentLength > 0) {
            requestSize.set(contentLength);
        }

        // 提取客户端IP
        String ip = request.getHeader("X-Forwarded-For");
        if(StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getHeader("X-Real-IP");
            if(StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        }
        getProcessData().setClientIp(ip);
    }

    public static ApikeyInfo getApikey() {
        return BellaContext.getApikey();
    }

    public static ApikeyInfo getApikeyIgnoreNull() {
        return BellaContext.getApikeyIgnoreNull();
    }

    public static void setApikey(ApikeyInfo ak) {
        BellaContext.setApikey(ak);
        EndpointContext.getProcessData().setApikeyInfo(ak);
    }

    public static void setEndpointData(String endpoint, String model, ChannelDB channel, Object request) {
        EndpointContext.getProcessData().setRequest(request);
        EndpointContext.getProcessData().setEndpoint(endpoint);
        EndpointContext.getProcessData().setModel(model);
        setCommonChannelData(channel);
    }

    public static void setEndpointData(String endpoint, String model, Object request) {
        EndpointContext.getProcessData().setRequest(request);
        EndpointContext.getProcessData().setEndpoint(endpoint);
        EndpointContext.getProcessData().setModel(model);
    }

    public static void setEndpointData(String endpoint, String model, Object request, String user) {
        setEndpointData(endpoint, model, request);
        EndpointContext.getProcessData().setUser(user);
    }

    public static void setEndpointData(ChannelDB channel) {
        boolean isPrivate = EntityConstants.PRIVATE.equals(channel.getVisibility());
        EndpointContext.getProcessData().setPrivate(isPrivate);
        boolean billingSkipped = isPrivate && (channel.getBillingEnabled() == null || channel.getBillingEnabled() == 0);
        EndpointContext.getProcessData().setBillingSkipped(billingSkipped);
        setCommonChannelData(channel);
        logBeforeModelRequest();
    }

    private static void setCommonChannelData(ChannelDB channel) {
        EndpointProcessData processData = EndpointContext.getProcessData();
        processData.setChannelCode(channel.getChannelCode());
        processData.setChannelEntityCode(channel.getEntityCode());
        processData.setForwardUrl(channel.getUrl());
        processData.setForwardMillis(DateTimeUtils.getCurrentMills());
        processData.setProtocol(channel.getProtocol());
        processData.setPriceInfo(channel.getPriceInfo());
        processData.setSupplier(channel.getSupplier());
        processData.setQueueName(channel.getQueueName());
        processData.setQueueMode(channel.getQueueMode());
        Map<String, Object> channelInfo = JacksonUtils.toMap(channel.getChannelInfo());
        processData.setDeployName(firstText(channelInfo, "deployName", "modelName", "deploymentName"));
        processData.setForwardHost(urlHost(channel.getUrl()));
        processData.setForwardPath(urlPath(channel.getUrl()));
    }

    private static String firstText(Map<String, Object> values, String... keys) {
        if(values == null || keys == null) {
            return Strings.EMPTY;
        }
        for (String key : keys) {
            Object value = values.get(key);
            if(value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return Strings.EMPTY;
    }

    private static String urlHost(String url) {
        if(StringUtils.isBlank(url)) {
            return Strings.EMPTY;
        }
        try {
            URI uri = new URI(url.trim());
            String host = StringUtils.lowerCase(uri.getHost(), Locale.ROOT);
            if(StringUtils.isBlank(host)) {
                return Strings.EMPTY;
            }
            return uri.getPort() >= 0 ? host + ":" + uri.getPort() : host;
        } catch (URISyntaxException ignored) {
            return Strings.EMPTY;
        }
    }

    private static String urlPath(String url) {
        if(StringUtils.isBlank(url)) {
            return Strings.EMPTY;
        }
        try {
            URI uri = new URI(url.trim());
            return StringUtils.defaultString(uri.getPath());
        } catch (URISyntaxException ignored) {
            return Strings.EMPTY;
        }
    }

    private static void logBeforeModelRequest() {
        EndpointProcessData data = EndpointContext.getProcessData();
        log.info("[nodeType=BEFORE_MODEL_REQUEST][traceId={}][requestId={}] endpoint={} model={} akCode={} channelCode={} protocol={} supplier={} deployName={} forwardHost={} forwardPath={}",
                data.getBellaTraceId(), data.getRequestId(), data.getEndpoint(), data.getModel(), data.getAkCode(),
                data.getChannelCode(), data.getProtocol(), data.getSupplier(), data.getDeployName(), data.getForwardHost(), data.getForwardPath());
    }

    public static void setEncodingType(String encodingType) {
        getProcessData().setEncodingType(encodingType);
    }

    public static void setHeaderInfo(Map<String, String> headers) {
        String maxWait = headers.get("X-BELLA-MAX-WAIT");
        if(StringUtils.isNumeric(maxWait)) {
            EndpointContext.getProcessData().setMaxWaitSec(Integer.parseInt(maxWait));
        }
    }

    public static void setEndpointData(String endpoint, ChannelDB channel, Object request) {
        setEndpointData(endpoint, Strings.EMPTY, channel, request);
    }

    public static void markLargeRequest() {
        isLastRequest.set(true);
    }

    public static boolean isLargeRequest() {
        return Boolean.TRUE.equals(isLastRequest.get());
    }

    public static Integer getRequestSize() {
        return requestSize.get();
    }

    public static void clearAll() {
        endpointRequestInfo.remove();
        requestCache.remove();
        isLastRequest.remove();
        requestSize.remove();
        BellaContext.clearAll();
    }

}

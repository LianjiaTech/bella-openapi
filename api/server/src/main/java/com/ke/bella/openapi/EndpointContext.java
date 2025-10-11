package com.ke.bella.openapi;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

public class EndpointContext {
    private static final ThreadLocal<EndpointProcessData> endpointRequestInfo = new ThreadLocal<>();

    private static final ThreadLocal<HttpServletRequest> requestCache = new ThreadLocal<>();


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


    public static HttpServletRequest getRequest() {
        Assert.notNull(requestCache.get(), "requestCache is empty");
        return requestCache.get();
    }

    public static void setRequest(HttpServletRequest request) {
        requestCache.set(request);
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
        EndpointContext.getProcessData().setChannelCode(channel.getChannelCode());
        EndpointContext.getProcessData().setForwardUrl(channel.getUrl());
        EndpointContext.getProcessData().setProtocol(channel.getProtocol());
        EndpointContext.getProcessData().setPriceInfo(channel.getPriceInfo());
        EndpointContext.getProcessData().setSupplier(channel.getSupplier());
    }

    public static void setEndpointData(String endpoint, String model, Object request) {
        EndpointContext.getProcessData().setRequest(request);
        EndpointContext.getProcessData().setEndpoint(endpoint);
        EndpointContext.getProcessData().setModel(model);
    }

    public static void setEndpointData(ChannelDB channel) {
        EndpointContext.getProcessData().setChannelCode(channel.getChannelCode());
        EndpointContext.getProcessData().setPrivate(EntityConstants.PRIVATE.equals(channel.getVisibility()));
        EndpointContext.getProcessData().setForwardUrl(channel.getUrl());
        EndpointContext.getProcessData().setProtocol(channel.getProtocol());
        EndpointContext.getProcessData().setPriceInfo(channel.getPriceInfo());
        EndpointContext.getProcessData().setSupplier(channel.getSupplier());
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

    public static void clearAll() {
        endpointRequestInfo.remove();
        requestCache.remove();
        BellaContext.clearAll();
    }

}

package com.ke.bella.openapi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.db.repo.ApikeyRoleRepo;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Data;
import org.apache.logging.log4j.util.Strings;
import org.springframework.util.Assert;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.math.BigDecimal;

public class BellaContext {
    private static final ThreadLocal<EndpointProcessData> endpointRequestInfo = new ThreadLocal<>();

    private static final ThreadLocal<ContentCachingRequestWrapper> requestCache = new ThreadLocal<>();

    private static final ThreadLocal<ApikeyInfo> akThreadLocal = new ThreadLocal<>();

    public static EndpointProcessData getProcessData() {
        if(endpointRequestInfo.get() == null) {

            endpointRequestInfo.set(new EndpointProcessData());
        }
        return endpointRequestInfo.get();
    }


    public static ContentCachingRequestWrapper getRequest() {
        Assert.notNull(requestCache.get(), "requestCache is empty");
        return requestCache.get();
    }

    public static void setRequest(ContentCachingRequestWrapper request) {
        requestCache.set(request);
    }


    public static ApikeyInfo getApikey() {
        Assert.notNull(akThreadLocal.get(), "ak is empty");
        return akThreadLocal.get();
    }

    public static void setApikey(ApikeyInfo ak) {
        akThreadLocal.set(ak);
        getProcessData().setAkCode(ak.getCode());
        getProcessData().setAccountType(ak.getOwnerType());
        getProcessData().setAccountCode(ak.getOwnerCode());
    }

    public static void setEndpointData(String endpoint, String model, ChannelDB channel, Object request) {
        BellaContext.getProcessData().setRequest(request);
        BellaContext.getProcessData().setEndpoint(endpoint);
        BellaContext.getProcessData().setModel(model);
        BellaContext.getProcessData().setChannelCode(channel.getChannelCode());
        BellaContext.getProcessData().setForwardUrl(channel.getUrl());
        BellaContext.getProcessData().setProtocol(channel.getProtocol());
        BellaContext.getProcessData().setPriceInfo(channel.getPriceInfo());
        BellaContext.getProcessData().setChannelInfo(channel.getChannelInfo());
    }

    public static void setEndpointData(String endpoint, ChannelDB channel, Object request) {
        setEndpointData(endpoint, Strings.EMPTY, channel, request);
    }

    public static void clearAll() {
        endpointRequestInfo.remove();
        requestCache.remove();
        akThreadLocal.remove();
    }

    @Data
    public static class ApikeyInfo {
        private String code;
        private String serviceId;
        private String akSha;
        private String parentCode;
        private String ownerType;
        private String ownerCode;
        private String ownerName;
        private String roleCode;
        @JsonIgnore
        private String path;
        private Byte safetyLevel;
        private BigDecimal monthQuota;
        private ApikeyRoleRepo.RolePath rolePath;
        private String status;
        public ApikeyRoleRepo.RolePath getRolePath() {
            if(path == null) {
                return new ApikeyRoleRepo.RolePath();
            }
            if(rolePath == null) {
                rolePath = JacksonUtils.deserialize(path, ApikeyRoleRepo.RolePath.class);
            }
            return rolePath;
        }
    }

}

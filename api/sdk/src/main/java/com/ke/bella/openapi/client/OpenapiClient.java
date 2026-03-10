package com.ke.bella.openapi.client;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.ke.bella.openapi.metadata.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.HttpHeaders;
import com.ke.bella.openapi.BellaResponse;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.metadata.Model;
import com.ke.bella.openapi.protocol.route.RouteRequest;
import com.ke.bella.openapi.protocol.route.RouteResult;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class OpenapiClient {
    private final String openapiHost;
    private final String serviceAk;

    private Cache<String, ApikeyInfo> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    private Cache<String, Model> modelCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    public OpenapiClient(String openapiHost) {
        this(openapiHost, null);
    }

    public OpenapiClient(String openapiHost, String serviceAk) {
        this.openapiHost = openapiHost;
        this.serviceAk = serviceAk;
    }

    public ApikeyInfo whoami(String apikey) {
        try {
            ApikeyInfo apikeyInfo = cache.get(apikey, () -> requestApikeyInfo(apikey));
            if(StringUtils.isEmpty(apikeyInfo.getCode())) {
                return null;
            }
            return apikeyInfo;
        } catch (ExecutionException e) {
            throw BellaException.fromException(e);
        }
    }

    public boolean validate(String apikey) {
        return whoami(apikey) != null;
    }

    public boolean hasPermission(String apikey, String url) {
        ApikeyInfo apikeyInfo = whoami(apikey);
        if(apikeyInfo != null) {
            return apikeyInfo.hasPermission(url);
        }
        return false;
    }

    private ApikeyInfo requestApikeyInfo(String apikey) {
        if(StringUtils.isEmpty(apikey)) {
            return null;
        }
        String url = openapiHost + "/v1/apikey/whoami";
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apikey)
                .build();
        BellaResponse<ApikeyInfo> bellaResp = HttpUtils.httpRequest(request, new TypeReference<BellaResponse<ApikeyInfo>>() {
        });
        return bellaResp == null || bellaResp.getData() == null ? new ApikeyInfo() : bellaResp.getData();
    }

    public Model getModelInfo(String modelName) {
        try {
            return modelCache.get(modelName, () -> requestModel(modelName));
        } catch (ExecutionException e) {
            throw BellaException.fromException(e);
        }
    }

    private Model requestModel(String modelName) {
        Assert.hasText(serviceAk, "serviceAk is null");
        if(StringUtils.isEmpty(modelName)) {
            return new Model();
        }
        String url = openapiHost + "/v1/meta/model/info/" + modelName;
        Request request = new Request.Builder()
                .get()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAk)
                .build();
        BellaResponse<Model> bellaResp = HttpUtils.httpRequest(request, new TypeReference<BellaResponse<Model>>() {
        });
        return bellaResp == null || bellaResp.getData() == null ? new Model() : bellaResp.getData();
    }

    public RouteResult route(String endpoint, String model, Integer queueMode, String userApikey) {
        Assert.hasText(serviceAk, "serviceAk is null");
        String url = openapiHost + "/v1/route";
        RouteRequest routeRequest = RouteRequest.builder().apikey(userApikey)
                .endpoint(endpoint).model(model).queueMode(queueMode).build();
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAk)
                .post(RequestBody.create(JacksonUtils.serialize(routeRequest), MediaType.parse("application/json")))
                .build();
        BellaResponse<RouteResult> bellaResp = HttpUtils.httpRequest(request, new TypeReference<BellaResponse<RouteResult>>() {
        });
        if(bellaResp.getCode() != 200) {
            throw BellaException.fromResponse(bellaResp.getCode(), bellaResp.getMessage());
        }
        return bellaResp.getData();
    }

    public Boolean log(EndpointProcessData processData) {
        Assert.hasText(serviceAk, "serviceAk is null");
        Assert.hasText(processData.getEndpoint(), "endpoint can not be null");
        Assert.hasText(processData.getAkSha(), "akSha can not be null");
        Assert.hasText(processData.getBellaTraceId(), "bella trace id can not be null");
        processData.setInnerLog(false);
        String url = openapiHost + "/v1/log";
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAk)
                .post(RequestBody.create(JacksonUtils.serialize(processData), MediaType.parse("application/json")))
                .build();
        BellaResponse<Boolean> bellaResp = HttpUtils.httpRequest(request, new TypeReference<BellaResponse<Boolean>>() {
        });
        if(bellaResp.getCode() != 200) {
            throw BellaException.fromResponse(bellaResp.getCode(), bellaResp.getMessage());
        }
        return bellaResp.getData();
    }

    @Deprecated
    public RouteResult route(String endpoint, String model, Integer queueMode, String userApikey, String consoleApikey) {
        String url = openapiHost + "/v1/route";
        RouteRequest routeRequest = RouteRequest.builder().apikey(userApikey)
                .endpoint(endpoint).model(model).queueMode(queueMode).build();
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + consoleApikey)
                .post(RequestBody.create(JacksonUtils.serialize(routeRequest), MediaType.parse("application/json")))
                .build();
        BellaResponse<RouteResult> bellaResp = HttpUtils.httpRequest(request, new TypeReference<BellaResponse<RouteResult>>() {
        });
        if(bellaResp.getCode() != 200) {
            throw BellaException.fromResponse(bellaResp.getCode(), bellaResp.getMessage());
        }
        return bellaResp.getData();
    }

    public Channel getChannelByQueue(String queueName) {
        Assert.hasText(serviceAk, "serviceAk is null");
        Assert.hasText(queueName, "queueName can not be null");
        String url = openapiHost + "/console/channels/" + queueName;
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAk)
                .get()
                .build();
        BellaResponse<Channel> bellaResp = HttpUtils.httpRequest(request,
                new TypeReference<BellaResponse<Channel>>() {
                });
        if(bellaResp.getCode() != 200) {
            throw BellaException.fromResponse(bellaResp.getCode(), bellaResp.getMessage());
        }
        return bellaResp.getData();
    }

    @Deprecated
    public Boolean log(EndpointProcessData processData, String consoleApikey) {
        Assert.hasText(processData.getEndpoint(), "endpoint can not be null");
        Assert.hasText(processData.getAkSha(), "akSha can not be null");
        Assert.hasText(processData.getBellaTraceId(), "bella trace id can not be null");
        processData.setInnerLog(false);
        String url = openapiHost + "/v1/log";
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + consoleApikey)
                .post(RequestBody.create(JacksonUtils.serialize(processData), MediaType.parse("application/json")))
                .build();
        BellaResponse<Boolean> bellaResp = HttpUtils.httpRequest(request, new TypeReference<BellaResponse<Boolean>>() {
        });
        if(bellaResp.getCode() != 200) {
            throw BellaException.fromResponse(bellaResp.getCode(), bellaResp.getMessage());
        }
        return bellaResp.getData();
    }
}

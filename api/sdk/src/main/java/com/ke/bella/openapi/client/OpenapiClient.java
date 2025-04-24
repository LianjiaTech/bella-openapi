package com.ke.bella.openapi.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.HttpHeaders;
import com.ke.bella.openapi.BellaResponse;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.Order;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.OpenapiListResponse;
import com.ke.bella.openapi.protocol.files.File;
import com.ke.bella.openapi.protocol.files.FileUrl;
import com.ke.bella.openapi.protocol.route.RouteRequest;
import com.ke.bella.openapi.protocol.route.RouteResult;
import com.ke.bella.openapi.utils.FileUtils;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

public class OpenapiClient {
    private final String openapiHost;
    private Cache<String, ApikeyInfo> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    public OpenapiClient(String openapiHost) {
        this.openapiHost = openapiHost;
    }

    public ApikeyInfo whoami(String apikey) {
        try {
            ApikeyInfo apikeyInfo = cache.get(apikey, () -> requestApikeyInfo(apikey));
            if(StringUtils.isEmpty(apikeyInfo.getCode())) {
                return null;
            }
            return apikeyInfo;
        } catch (ExecutionException e) {
            throw ChannelException.fromException(e);
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

    public FileUrl getFileUrl(String apikey, String fileId) {
        return getFileUrl(apikey, fileId, null);
    }

    public FileUrl getFileUrl(String apikey, String fileId, Long expires) {
        if(StringUtils.isEmpty(apikey)) {
            throw new IllegalArgumentException(String.format("apiKey = %s must not be empty", apikey));
        }

        String url = openapiHost + "/v1/files/" + fileId + "/url";

        if(expires != null) {
            url += "?expires=" + expires;
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apikey)
                .build();
        return HttpUtils.doHttpRequest(request, new TypeReference<FileUrl>() {
        });
    }

    public byte[] retrieveFileContent(String apiKey, String fileId) {
        String url = openapiHost + "/v1/files/" + fileId + "/content";
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        return HttpUtils.doHttpRequest(request);
    }

    public File getFile(String apiKey, String fileId, boolean getUrl, Long expires) {
        String url = openapiHost + "/v1/files/" + fileId;

        if(getUrl) {
            url += "?get_url=true";
            if(expires != null) {
                url += "&expires=" + expires;
            }
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        return HttpUtils.doHttpRequest(request, new TypeReference<File>() {
        });
    }

    public File getFile(String apiKey, String fileId) {
        String url = openapiHost + "/v1/files/" + fileId;
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        return HttpUtils.doHttpRequest(request, new TypeReference<File>() {
        });
    }

    public String getPreviewUrl(String apiKey, String fileId) {
        String url = openapiHost + "/v1/files/" + fileId + "/preview_url";
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        FileUrl fileUrl = HttpUtils.doHttpRequest(request, new TypeReference<FileUrl>() {
        });
        if(fileUrl == null || StringUtils.isBlank(fileUrl.getUrl())) {
            throw new IllegalStateException("failed to get preview url");
        }
        return fileUrl.getUrl();
    }

    public File uploadFile(String apiKey, String purpose, InputStream fileInputStream, String filename) {
        return uploadFile(apiKey, purpose, FileUtils.readAllBytes(fileInputStream), filename);
    }

    public File uploadFile(String apiKey, String purpose, byte[] bytes, String filename) {
        String url = openapiHost + "/v1/files";

        MediaType mediaType = FileUtils.extraMediaType(filename);

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, RequestBody.create(bytes, mediaType))
                .addFormDataPart("purpose", purpose)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .post(requestBody)
                .build();

        return HttpUtils.doHttpRequest(request, new TypeReference<File>() {
        });
    }

    public List<File> listFiles(String apiKey) {
        return listFiles(apiKey, null, null, null, null, false, null);
    }

    public List<File> listFiles(String apiKey, String purpose, Integer limit, String after, Order order) {
        return listFiles(apiKey, purpose, limit, after, order, false, null);
    }

    public List<File> listFiles(String apiKey, List<String> fileIds) {
        return listFiles(apiKey, fileIds, false, null);
    }

    public List<File> listFiles(String apiKey, List<String> fileIds, boolean getUrl, Long expires) {
        String url = openapiHost + "/v1/files/list";
        Map<String, Object> body = new HashMap<>();
        if(getUrl) {
            body.put("get_url", getUrl);
            body.put("expires", expires);
        }
        body.put("file_ids", fileIds);

        RequestBody requestBody = RequestBody.create(JacksonUtils.serialize(body), MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        return HttpUtils.doHttpRequest(request, new TypeReference<List<File>>() {
        });
    }

    public List<File> listFiles(String apiKey, String purpose, Integer limit, String after, Order order, boolean getUrl, Long expires) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(openapiHost + "/v1/files");

        if(!StringUtils.isEmpty(purpose)) {
            builder.queryParam("purpose", purpose);
        }
        if(limit != null && limit > 0) {
            builder.queryParam("limit", limit);
        }
        if(!StringUtils.isEmpty(after)) {
            builder.queryParam("after", after);
        }
        if(order != null) {
            builder.queryParam("order", order.name());
        }
        if(getUrl) {
            builder.queryParam("get_url", true);
            if(expires != null) {
                builder.queryParam("expires", expires);
            }
        }

        String url = builder.build().toUriString();
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        OpenapiListResponse<File> response = HttpUtils.doHttpRequest(request, new TypeReference<OpenapiListResponse<File>>() {
        });

        return response == null ? null : response.getData();
    }

    public File deleteFile(String apiKey, String fileId) {
        String url = openapiHost + "/v1/files/" + fileId;
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .delete()
                .build();
        return HttpUtils.doHttpRequest(request, new TypeReference<File>() {
        });
    }

    public RouteResult route(String endpoint, String model, String userApikey, String consoleApikey) {
        String url = openapiHost + "/v1/route";
        RouteRequest routeRequest = RouteRequest.builder().apikey(userApikey).endpoint(endpoint).model(model).build();
        Request request = new Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + consoleApikey)
                .post(RequestBody.create(JacksonUtils.serialize(routeRequest), MediaType.parse("application/json")))
                .build();
        BellaResponse<RouteResult> bellaResp = HttpUtils.httpRequest(request, new TypeReference<BellaResponse<RouteResult>>() {
        });
        if(bellaResp.getCode() != 200) {
            throw ChannelException.fromResponse(bellaResp.getCode(), bellaResp.getMessage());
        }
        return bellaResp.getData();
    }

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
            throw ChannelException.fromResponse(bellaResp.getCode(), bellaResp.getMessage());
        }
        return bellaResp.getData();
    }
}

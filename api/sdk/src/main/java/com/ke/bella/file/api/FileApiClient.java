package com.ke.bella.file.api;

import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

public class FileApiClient {
    private final String url;
    private static volatile FileApiClient INSTANCE;

    public static FileApiClient getInstance(String url) {
        if(INSTANCE == null) {
            synchronized(FileApiClient.class) {
                if(INSTANCE == null) {
                    INSTANCE = new FileApiClient(url);
                }
            }
        }
        return INSTANCE;
    }

    private FileApiClient(String url) {
        if (StringUtils.isBlank(url)) {
            throw new IllegalStateException("File API URL is not configured.");
        }
        this.url = url;
    }

    public byte[] getContent(String fileId, String apikey) {
        Request requestFile = new Request.Builder()
                .url(url + "/v1/files/" + fileId + "/content")
                .addHeader("Authorization", "Bearer " + apikey)
                .get()
                .build();
        return HttpUtils.doHttpRequest(requestFile);
    }

    public void writeContent(String fileId, String apikey, File file) {
        Request requestFile = new Request.Builder()
                .url(url + "/v1/files/" + fileId + "/content")
                .addHeader("Authorization", "Bearer " + apikey)
                .get()
                .build();
        HttpUtils.doHttpRequest(requestFile, file);
    }
}

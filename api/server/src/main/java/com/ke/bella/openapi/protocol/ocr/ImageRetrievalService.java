package com.ke.bella.openapi.protocol.ocr;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.service.OpenAiService;

import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;

/**
 * 图片获取服务
 * 用于从文件ID获取图片数据的通用服务
 */
@Slf4j
@Service
public class ImageRetrievalService {

    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;

    /**
     * 从文件ID获取图片数据
     *
     * @param fileId 文件ID
     * @return 图片字节数据
     * @throws IllegalStateException 当获取图片失败时抛出
     */
    public byte[] getImageFromFileId(String fileId) {
        OpenAiService openAiService = openAiServiceFactory.create(10, 60);
        try (ResponseBody responseBody = openAiService.retrieveFileContent(fileId)) {
            return responseBody.bytes();
        } catch (IOException e) {
            log.error("Failed to retrieve image from fileId: {}", fileId, e);
            throw new IllegalStateException(e);
        }
    }
}
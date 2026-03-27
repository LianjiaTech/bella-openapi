package com.ke.bella.openapi.service;

import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.file.File;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AudioFileService {
    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;

    public String uploadAndGetUrl(byte[] content, String filename) {
        String uniqueName = UUID.randomUUID() + "_" + filename;
        OpenAiService service = openAiServiceFactory.create();
        File file = service.uploadFile("temp", content, uniqueName);
        return service.retrieveFileUrl(file.getId()).getUrl();
    }
}

package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.file.FileUrl;
import com.theokanning.openai.service.OpenAiService;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.asr.QwenProperty;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.theokanning.openai.file.File;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component("QwenFlashAsr")
public class QwenAdaptor implements FlashAsrAdaptor<QwenProperty> {

    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;

    @Override
    public FlashAsrResponse asr(AsrRequest request, String url, QwenProperty property, EndpointProcessData processData) {
        try {
            // Upload audio file and get URL
            String audioUrl = uploadAudioAndGetUrl(request);
            
            // Build Alibaba ASR request
            QwenFlashAsrRequest aliRequest = buildAliRequest(request, audioUrl, property);
            
            // Make HTTP request to Alibaba
            Request httpRequest = buildHttpRequest(aliRequest, url, property);
            QwenFlashAsrResponse aliResponse = HttpUtils.httpRequest(httpRequest, QwenFlashAsrResponse.class);
            
            // Convert to FlashAsrResponse
            return convertToFlashAsrResponse(aliResponse, processData);
            
        } catch (Exception e) {
            throw ChannelException.fromException(e);
        }
    }

    private String uploadAudioAndGetUrl(AsrRequest request) {
        OpenAiService openAiService = openAiServiceFactory.create();
        
        // Upload file with proper filename based on format
        String filename = UUID.randomUUID().toString() + "_audio." + (request.getFormat() != null ? request.getFormat() : "wav");
        
        // Create file upload request
        File openAiFile = openAiService.uploadFile("temp", request.getContent(), filename);
        
        FileUrl fileUrlResponse = openAiService.retrieveFileUrl(openAiFile.getId());

        return fileUrlResponse.getUrl();
    }

    private QwenFlashAsrRequest buildAliRequest(AsrRequest request, String audioUrl, QwenProperty property) {
        // Build system message
        QwenFlashAsrRequest.Content systemContent = QwenFlashAsrRequest.Content.builder()
                .text("")
                .build();
        
        QwenFlashAsrRequest.Message systemMessage = QwenFlashAsrRequest.Message.builder()
                .content(Arrays.asList(systemContent))
                .role("system")
                .build();
        
        // Build user message with audio
        QwenFlashAsrRequest.Content audioContent = QwenFlashAsrRequest.Content.builder()
                .audio(audioUrl)
                .build();
        
        QwenFlashAsrRequest.Message userMessage = QwenFlashAsrRequest.Message.builder()
                .content(Arrays.asList(audioContent))
                .role("user")
                .build();
        
        // Build input
        QwenFlashAsrRequest.Input input = QwenFlashAsrRequest.Input.builder()
                .messages(Arrays.asList(systemMessage, userMessage))
                .build();
        
        // Build ASR options
        QwenFlashAsrRequest.AsrOptions asrOptions = QwenFlashAsrRequest.AsrOptions.builder()
                .enableLid(true)
                .enableItn(false)
                .build();
        
        QwenFlashAsrRequest.Parameters parameters = QwenFlashAsrRequest.Parameters.builder()
                .asrOptions(asrOptions)
                .build();
        
        // Use model from property or request
        String model = StringUtils.hasText(property.getDeployName()) ? 
                property.getDeployName() : request.getModel();
        
        return QwenFlashAsrRequest.builder()
                .model(model)
                .input(input)
                .parameters(parameters)
                .build();
    }

    private Request buildHttpRequest(QwenFlashAsrRequest aliRequest, String url, QwenProperty property) {
        String requestBody = JacksonUtils.serialize(aliRequest);
        
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody));
        
        return builder.build();
    }

    private FlashAsrResponse convertToFlashAsrResponse(QwenFlashAsrResponse aliResponse, EndpointProcessData processData) {
        List<FlashAsrResponse.Sentence> sentences = new ArrayList<>();
        
        if (aliResponse.getOutput() != null && 
            !CollectionUtils.isEmpty(aliResponse.getOutput().getChoices())) {
            
            QwenFlashAsrResponse.Choice choice = aliResponse.getOutput().getChoices().get(0);
            if (choice.getMessage() != null && 
                !CollectionUtils.isEmpty(choice.getMessage().getContent())) {
                
                // Extract text from all content items
                StringBuilder fullText = new StringBuilder();
                for (QwenFlashAsrResponse.Content content : choice.getMessage().getContent()) {
                    if (StringUtils.hasText(content.getText())) {
                        fullText.append(content.getText());
                    }
                }
                
                // Create a single sentence with the full text
                if (fullText.length() > 0) {
                    FlashAsrResponse.Sentence sentence = FlashAsrResponse.Sentence.builder()
                            .text(fullText.toString())
                            .beginTime(0L)
                            .endTime(0L) // We don't have timing info from Alibaba response
                            .build();
                    sentences.add(sentence);
                }
            }
        }
        
        // Calculate duration from usage if available
        int duration = 0;
        if (aliResponse.getUsage() != null && aliResponse.getUsage().getSeconds() != null) {
            duration = aliResponse.getUsage().getSeconds();
        }
        
        FlashAsrResponse.FlashResult flashResult = FlashAsrResponse.FlashResult.builder()
                .duration(duration)
                .sentences(sentences)
                .build();
        
        return FlashAsrResponse.builder()
                .taskId(processData.getChannelRequestId())
                .user(processData.getUser())
                .flashResult(flashResult)
                .build();
    }

    @Override
    public String getDescription() {
        return "通义千问协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return QwenProperty.class;
    }
}
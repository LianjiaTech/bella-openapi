package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.service.OpenAiService;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.asr.AliProperty;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.ke.bella.openapi.protocol.files.FileUrl;
import com.theokanning.openai.file.File;
import com.ke.bella.openapi.server.OpenapiProperties;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component("AliFlashAsr")
public class AliAdaptor implements FlashAsrAdaptor<AliProperty> {
    
    private final OpenapiProperties openapiProperties;
    private final OpenAiServiceFactory openAiServiceFactory;
    
    public AliAdaptor(OpenapiProperties openapiProperties, OpenAiServiceFactory openAiServiceFactory) {
        this.openapiProperties = openapiProperties;
        this.openAiServiceFactory = openAiServiceFactory;
    }

    @Override
    public FlashAsrResponse asr(AsrRequest request, String url, AliProperty property, EndpointProcessData processData) {
        try {
            // Upload audio file and get URL
            String audioUrl = uploadAudioAndGetUrl(request, processData);
            
            // Build Alibaba ASR request
            AliFlashAsrRequest aliRequest = buildAliRequest(request, audioUrl, property);
            
            // Make HTTP request to Alibaba
            Request httpRequest = buildHttpRequest(aliRequest, url, property);
            AliFlashAsrResponse aliResponse = HttpUtils.httpRequest(httpRequest, AliFlashAsrResponse.class);
            
            // Convert to FlashAsrResponse
            return convertToFlashAsrResponse(aliResponse, processData);
            
        } catch (Exception e) {
            throw ChannelException.fromException(e);
        }
    }

    private String uploadAudioAndGetUrl(AsrRequest request, EndpointProcessData processData) {
        OpenAiService openAiService = openAiServiceFactory.create(processData.getApikey());
        
        // Upload file with proper filename based on format
        String filename = UUID.randomUUID().toString() + "_audio." + (request.getFormat() != null ? request.getFormat() : "wav");
        
        // Create file upload request
        File openAiFile = openAiService.uploadFile("temp", request.getContent(), filename);
        
        // Get file URL by making a request to the file URL endpoint
        String fileUrlEndpoint = openapiProperties.getHost() + "/v1/files/" + openAiFile.getId() + "/url";

        Request urlRequest = new Request.Builder()
                .url(fileUrlEndpoint)
                .addHeader("Authorization", "Bearer " + processData.getApikey())
                .get()
                .build();
        
        FileUrl fileUrlResponse = HttpUtils.httpRequest(urlRequest, FileUrl.class);

        return fileUrlResponse.getUrl();
    }

    private AliFlashAsrRequest buildAliRequest(AsrRequest request, String audioUrl, AliProperty property) {
        // Build system message
        AliFlashAsrRequest.Content systemContent = AliFlashAsrRequest.Content.builder()
                .text("")
                .build();
        
        AliFlashAsrRequest.Message systemMessage = AliFlashAsrRequest.Message.builder()
                .content(Arrays.asList(systemContent))
                .role("system")
                .build();
        
        // Build user message with audio
        AliFlashAsrRequest.Content audioContent = AliFlashAsrRequest.Content.builder()
                .audio(audioUrl)
                .build();
        
        AliFlashAsrRequest.Message userMessage = AliFlashAsrRequest.Message.builder()
                .content(Arrays.asList(audioContent))
                .role("user")
                .build();
        
        // Build input
        AliFlashAsrRequest.Input input = AliFlashAsrRequest.Input.builder()
                .messages(Arrays.asList(systemMessage, userMessage))
                .build();
        
        // Build ASR options
        AliFlashAsrRequest.AsrOptions asrOptions = AliFlashAsrRequest.AsrOptions.builder()
                .enableLid(true)
                .enableItn(false)
                .build();
        
        AliFlashAsrRequest.Parameters parameters = AliFlashAsrRequest.Parameters.builder()
                .asrOptions(asrOptions)
                .build();
        
        // Use model from property or request
        String model = StringUtils.hasText(property.getDeployName()) ? 
                property.getDeployName() : request.getModel();
        
        return AliFlashAsrRequest.builder()
                .model(model)
                .input(input)
                .parameters(parameters)
                .build();
    }

    private Request buildHttpRequest(AliFlashAsrRequest aliRequest, String url, AliProperty property) {
        String requestBody = JacksonUtils.serialize(aliRequest);
        
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody));
        
        return builder.build();
    }

    private FlashAsrResponse convertToFlashAsrResponse(AliFlashAsrResponse aliResponse, EndpointProcessData processData) {
        List<FlashAsrResponse.Sentence> sentences = new ArrayList<>();
        
        if (aliResponse.getOutput() != null && 
            !CollectionUtils.isEmpty(aliResponse.getOutput().getChoices())) {
            
            AliFlashAsrResponse.Choice choice = aliResponse.getOutput().getChoices().get(0);
            if (choice.getMessage() != null && 
                !CollectionUtils.isEmpty(choice.getMessage().getContent())) {
                
                // Extract text from all content items
                StringBuilder fullText = new StringBuilder();
                for (AliFlashAsrResponse.Content content : choice.getMessage().getContent()) {
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
        return "阿里巴巴协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AliProperty.class;
    }
}
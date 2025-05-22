package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.endpoints.ChatController;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.common.exception.FallbackException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ModelFallbackHandler {

    @Autowired
    private ChannelRouter router;
    
    @Autowired
    private AdaptorManager adaptorManager;
    
    @Autowired
    private LimiterManager limiterManager;
    
    @Autowired
    private ISafetyCheckService.IChatSafetyCheckService safetyCheckService;
    
    @Value("${bella.openapi.fallback.max-models:3}")
    private int maxFallbackModels;
    
    @Value("${bella.openapi.fallback.default-timeout-seconds:30}")
    private int defaultTimeoutSeconds;

    @Autowired
    private ChatController chatController;

    /**
     * Handles the completion request with fallback models if specified
     */
    public CompletionResponse handleCompletion(CompletionRequest request, String endpoint, 
                                              CompletionAdaptor<?> baseAdaptor, Object requestRiskData) {
        request.parseModelFallbacks();
        
        // If no fallback models, just execute normally
        if (CollectionUtils.isEmpty(request.getFallbackModels())) {
            return executeCompletion(request, endpoint, baseAdaptor, requestRiskData);
        }
        
        // Get timeout from header or use default
        int timeoutSeconds = getTimeoutFromHeader();
        long startTime = System.currentTimeMillis();
        long endTime = startTime + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        
        // Try the primary model first
        Exception primaryException;
        List<String> attemptedModels = new ArrayList<>();
        attemptedModels.add(request.getModel());
        
        try {
            return executeCompletion(request, endpoint, baseAdaptor, requestRiskData);
        } catch (Exception e) {
            log.warn("Primary model {} failed: {}", request.getModel(), e.getMessage());
            
            primaryException = e;
            // Limit the number of fallback models to try
            int modelsToTry = Math.min(request.getFallbackModels().size(), maxFallbackModels - 1);
            
            List<Exception> exceptions = new ArrayList<>();
            // Try each fallback model
            for (int i = 0; i < modelsToTry; i++) {
                // Check if we've exceeded the timeout
                if (System.currentTimeMillis() >= endTime) {
                    throw new ChannelException.TimeoutException("Fallback models timeout after " + timeoutSeconds + " seconds");
                }
                
                String fallbackModel = request.getFallbackModels().get(i);
                attemptedModels.add(fallbackModel);
                
                try {
                    // Create a copy of the request with the fallback model
                    CompletionRequest fallbackRequest = JacksonUtils.convert(request, CompletionRequest.class);
                    fallbackRequest.setModel(fallbackModel);
                    fallbackRequest.setFallbackModels(null);
                    
                    // Execute with fallback model
                    CompletionResponse response = executeCompletion(fallbackRequest, endpoint, baseAdaptor, requestRiskData);
                    log.info("Successfully completed request with fallback model: {}", fallbackModel);
                    return response;
                } catch (Exception fallbackEx) {
                    log.warn("Fallback model {} failed: {}", fallbackModel, fallbackEx.getMessage());
                    exceptions.add(fallbackEx);
                }
            }
            
            // If all fallbacks failed, throw a FallbackException
            throw new FallbackException("All models failed, including fallbacks", 
                attemptedModels, exceptions);
        }
    }
    
    private int getTimeoutFromHeader() {
        String timeoutHeader = EndpointContext.getRequest().getHeader("X-BELLA-FALLBACK-TIMEOUT");
        if (StringUtils.isNotEmpty(timeoutHeader)) {
            try {
                int timeout = Integer.parseInt(timeoutHeader);
                return Math.max(1, Math.min(timeout, 180)); // Limit between 1-180 seconds
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
        }
        return defaultTimeoutSeconds;
    }
    
    private CompletionResponse executeCompletion(CompletionRequest request, String endpoint, 
                                              CompletionAdaptor<?> baseAdaptor, Object requestRiskData) {
        return chatController.executeCompletion(request, endpoint, baseAdaptor, requestRiskData);
    }
}

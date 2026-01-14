package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.gemini.GeminiAdaptor;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiRequest;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Gemini API Controller
 * <p>
 * 提供与 Google Gemini API 兼容的接口，支持阻塞和流式生成。
 * 响应直接从后端（Vertex AI）透传给客户端，以保持协议的一致性。
 * </p>
 *
 * @see com.ke.bella.openapi.protocol.gemini.VertexAdaptor
 */
@EndpointAPI
@RestController
@RequestMapping("/v1beta/models")
@Tag(name = "gemini", description = "Google Gemini API Compatible Endpoints")
@Slf4j
public class GeminiController {

    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointDataService endpointDataService;

    /**
     * 处理 Gemini API 内容生成请求。
     *
     * @param model        模型名称 (例如 "gemini-2.5-flash")
     * @param method       方法名称 ("generateContent" 或 "streamGenerateContent")
     * @param bodyBytes    原始请求体
     * @param httpResponse HTTP 响应对象，用于直接写入
     * @throws IOException            I/O 错误时抛出
     * @throws BizParamCheckException 参数无效时抛出
     */
    @PostMapping("/{model}:{method}")
    @Operation(summary = "Gemini API Content Generation",
            description = "Generate content using Gemini models with blocking or streaming mode")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void generateContent(
            @Parameter(description = "Model name (e.g., gemini-2.5-flash, gemini-pro)", required = true)
            @PathVariable("model") String model,
            @Parameter(description = "Method name: generateContent or streamGenerateContent", required = true)
            @PathVariable("method") String method,
            @RequestBody byte[] bodyBytes,
            HttpServletResponse httpResponse) throws IOException {

        // ==================== 1. 策略选择 ====================
        boolean isStream = validateAndCheckStreamMode(method);

        // ==================== 2. 请求解析 ====================
        GeminiRequest geminiRequest = parseRequestBody(bodyBytes);

        // ==================== 3. 上下文设置 ====================
        // 设置 endpoint 数据,触发 AOP 拦截器和路由逻辑
        // 从请求 URI 中动态提取基础路径 (去掉 /{model}:{method} 后缀)
        String requestUri = EndpointContext.getRequest().getRequestURI();
        String endpoint = requestUri.substring(0, requestUri.indexOf("/" + model));
        endpointDataService.setEndpointData(endpoint, model, geminiRequest);
        boolean isMock = EndpointContext.getProcessData().isMock();

        // ==================== 4. Channel 路由 ====================
        // 根据 model 名称路由到对应的 Channel
        // endpoint 参数在有 model 时被忽略 (只用于 adaptor 查找)
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), isMock);
        endpointDataService.setChannel(channel);

        // ==================== 5. 并发限流 ====================
        // 非私有 Channel 需要增加并发计数
        if (!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(
                EndpointContext.getProcessData().getAkCode(),
                model
            );
        }

        // ==================== 6. 获取处理数据 ====================
        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();

        // ==================== 7. 适配器获取 ====================
        // 通过 AdaptorManager 统一管理获取 GeminiAdaptor
        GeminiAdaptor adaptor = adaptorManager.getProtocolAdaptor(
            endpoint,
            protocol,
            GeminiAdaptor.class
        );

        if (adaptor == null) {
            throw new BizParamCheckException("Unsupported protocol: " + protocol + " for Gemini endpoint");
        }

        // ==================== 8. Channel 配置解析 ====================
        CompletionProperty property = (CompletionProperty) JacksonUtils.deserialize(
            channelInfo,
            adaptor.getPropertyClass()
        );
        EndpointContext.setEncodingType(property.getEncodingType());

        // ==================== 9. 策略执行 ====================
        // 根据 isStream 标志调用不同的处理策略
        // 响应直接透传到 httpResponse,不经过格式转换
        if (isStream) {
            adaptor.streamCompletion(geminiRequest, url, property, httpResponse);
        } else {
            adaptor.completion(geminiRequest, url, property, httpResponse);
        }

        // 注意:
        // 1. 响应已由 adaptor 直接写入 httpResponse
        // 2. 日志记录由 adaptor 内部通过 TaskExecutor 异步处理
        // 3. 不触发 EndpointResponseAdvice (因为返回 void)
    }

    /**
     * 验证 method 参数并确定流式模式
     *
     * @param method 方法名称 (不区分大小写)
     * @return true 表示流式模式, false 表示阻塞模式
     * @throws BizParamCheckException method 参数不支持
     */
    private boolean validateAndCheckStreamMode(String method) {
        switch (method.toLowerCase()) {
            case "generatecontent":
                return false;
            case "streamgeneratecontent":
                return true;
            default:
                throw new BizParamCheckException(
                    "Unsupported method: " + method + ". " +
                    "Supported methods: generateContent, streamGenerateContent"
                );
        }
    }

    /**
     * 解析请求体为 GeminiRequest
     *
     * @param bodyBytes 请求体字节数组
     * @return 解析后的 GeminiRequest 对象
     * @throws BizParamCheckException 请求体为空或解析失败
     */
    private GeminiRequest parseRequestBody(byte[] bodyBytes) {
        try {
            GeminiRequest request = JacksonUtils.deserialize(bodyBytes, GeminiRequest.class);
            if (request == null) {
                 throw new BizParamCheckException("Empty request body");
            }
            return request;
        } catch (Exception e) {
            log.error("Failed to parse GeminiRequest", e);
            throw new BizParamCheckException("Invalid Gemini request format: " + e.getMessage());
        }
    }
}

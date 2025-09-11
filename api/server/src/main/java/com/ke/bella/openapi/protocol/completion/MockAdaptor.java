package com.ke.bella.openapi.protocol.completion;

import static com.ke.bella.openapi.protocol.completion.ResponseHelper.END_THINK;
import static com.ke.bella.openapi.protocol.completion.ResponseHelper.START_THINK;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.mock.ContentGenerator;
import com.ke.bella.openapi.mock.FunctionCallGenerator;
import com.ke.bella.openapi.mock.MockNetworkIO;
import com.ke.bella.openapi.mock.MockSseWriter;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Data;

@Component("mock")
public class MockAdaptor implements CompletionAdaptorDelegator<CompletionProperty> {

    ExecutorService executor = new ThreadPoolExecutor(500, 500,
            0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000),
            new DefaultThreadFactory("mock-completion"));

    MockNetworkIO mockNetworkIO = new MockNetworkIO(executor);

    @Override
    public String getDescription() {
        return "mock协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return CompletionProperty.class;
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, CompletionProperty property) {
        MockCompletionRequest mockCompletionRequest = new MockCompletionRequest((CompletionRequest) EndpointContext.getProcessData().getRequest(), BellaContext.getHeaders(), property);
        CompletionResponse response = new CompletionResponse();
        response.setCreated(DateTimeUtils.getCurrentSeconds());
        response.setModel("mock-model");
        response.setId("chatcmpl-" + UUID.randomUUID());
        CompletionResponse.Choice choice = new CompletionResponse.Choice();
        response.setChoices(Lists.newArrayList(choice));
        choice.setIndex(0);
        Message message = new Message();
        message.setRole("assistant");
        choice.setMessage(message);
        if(CollectionUtils.isNotEmpty(mockCompletionRequest.getToolCalls())) {
            choice.setFinish_reason("stop");
            message.setContent(mockCompletionRequest.getContent());
        } else {
            choice.setFinish_reason("tool_calls");
            message.setTool_calls(mockCompletionRequest.getToolCalls());
        }
        return mockNetworkIO.httpRequest(response, mockCompletionRequest.getTtlt());
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, CompletionProperty property, Callbacks.StreamCompletionCallback callback) {
        MockCompletionRequest mockCompletionRequest = new MockCompletionRequest((CompletionRequest) EndpointContext.getProcessData().getRequest(), BellaContext.getHeaders(), property);
        CompletionSseListener sseListener = new CompletionSseListener(callback, new Callbacks.DefaultSseConverter());
        List<StreamCompletionResponse> chunks = mockCompletionRequest.getChunks();
        CompletableFuture<?> future = new CompletableFuture<>();
        sseListener.setConnectionInitFuture(future);
        mockNetworkIO.sseRequest(mockSseWriter(sseListener), chunks, mockCompletionRequest.getTtft(), mockCompletionRequest.getInterval());
        try {
            future.get();
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
            Thread.currentThread().interrupt();
        }  catch (ExecutionException e) {
            if(e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    private MockSseWriter mockSseWriter(CompletionSseListener sseListener) {
        return new MockSseWriter() {
            @Override
            public void onOpen() {
                sseListener.onOpen(null, null);
            }

            @Override
            public void onWrite(Object chunk) {
                sseListener.onEvent(null, null, null, chunk instanceof String ? (String) chunk : JacksonUtils.serialize(chunk));
            }

            @Override
            public void onCompletion() {
                sseListener.onClosed(null);
            }

            @Override
            public void onError(Throwable error) {
                sseListener.onFailure(null, error, null);
            }
        };
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, CompletionProperty property, Callbacks.HttpDelegator delegator) {
        return completion(request, url, property);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, CompletionProperty property, Callbacks.StreamCompletionCallback callback,
            Callbacks.StreamDelegator delegator) {
        streamCompletion(request, url, property, callback);
    }

    @Data
    public static class MockCompletionRequest {
        private String content;
        private String reasoning;
        private List<Message.ToolCall> toolCalls;
        private Integer ttft = 500;
        private Integer ttlt = 10 * 1000;
        private Integer interval = 100;
        private CompletionProperty property;
        private String id = "chatcmpl-" + UUID.randomUUID();

        public MockCompletionRequest(CompletionRequest request, Map<String, String> requestInfo, CompletionProperty property) {
            this.property = property;
            String ttft = requestInfo.get("X-BELLA-MOCK-TTFT");
            String ttlt = requestInfo.get("X-BELLA-MOCK-TTLT");
            String interval = requestInfo.get("X-BELLA-MOCK-INTERVAL");
            String function = requestInfo.get("X-BELLA-MOCK-FUNCTION");
            String text = requestInfo.get("X-BELLA-MOCK-TEXT");
            String reasoning = requestInfo.get("X-BELLA-MOCK-REASONING");
            if(StringUtils.isNumeric(ttft)) {
                this.ttft = Integer.parseInt(ttft);
            }
            if(StringUtils.isNumeric(ttlt)) {
                this.ttlt = Integer.parseInt(ttlt);
            }
            if(StringUtils.isNumeric(interval)) {
                this.interval = Integer.parseInt(interval);
            }
            if(StringUtils.isNotBlank(reasoning)) {
                this.reasoning = reasoning;
            } else {
                if(property.isMergeReasoningContent() || property.isSplitReasoningFromContent()) {
                    this.reasoning = ContentGenerator.generateContent(200);
                }
            }
            if(StringUtils.isBlank(text) && StringUtils.isBlank(function)) {
                if(CollectionUtils.isNotEmpty(request.getTools())) {
                    if(property.isFunctionCallSimulate()) {
                        this.content = FunctionCallGenerator.generatePythonCode(request.getTools(), true);
                    } else {
                        this.toolCalls = FunctionCallGenerator.generateToolCalls(request.getTools(), true);
                    }
                } else  {
                    this.content = ContentGenerator.generateContent(500);
                }
            } else {
                try {
                    if(StringUtils.isNotBlank(text)) {
                        text = URLDecoder.decode(text, StandardCharsets.UTF_8.toString());
                        this.content = text;
                    } else {
                        function = URLDecoder.decode(function, StandardCharsets.UTF_8.toString());
                        Message.ToolCall toolCall = new Message.ToolCall();
                        toolCall.setId(UUID.randomUUID().toString());
                        toolCall.setIndex(0);
                        toolCall.setType("function");
                        toolCall.setFunction(JacksonUtils.deserialize(function, Message.FunctionCall.class));
                        this.toolCalls = Lists.newArrayList(toolCall);
                    }
                } catch (UnsupportedEncodingException e) {
                    throw new BizParamCheckException(e.getMessage());
                }
            }
        }

        public List<StreamCompletionResponse> getChunks() {
            List<StreamCompletionResponse> chunks = new ArrayList<>();
            if(property.isSplitReasoningFromContent()) {
                chunks.add(getChunk(START_THINK, false));
            }
            if(StringUtils.isNotBlank(reasoning)) {
                chunks.addAll(getTextChunks(reasoning, true));
            }
            if(property.isSplitReasoningFromContent()) {
                chunks.add(getChunk(END_THINK, false));
            }
            if(StringUtils.isNotBlank(content)) {
                chunks.addAll(getTextChunks(content, false));
            }
            if(CollectionUtils.isNotEmpty(toolCalls)) {
                toolCalls.forEach(toolCall -> {
                    chunks.add(getChunk(toolCall, true));
                    chunks.add(getChunk(toolCall, false));
                });
            }
            chunks.add(getStopChunk());
            chunks.add(getUsageChunk());
            return chunks;
        }

        private List<StreamCompletionResponse> getTextChunks(String text, boolean isReason) {
            List<StreamCompletionResponse> chunks = new ArrayList<>();
            if(StringUtils.isBlank(text)) {
                return chunks;
            }

            // 计算总的分包数
            int totalChunks = (ttlt - ttft) / interval;

            // 如果同时存在 content 和 reasoning，按照 3:7 的比例分配
            if(StringUtils.isNotBlank(content) && StringUtils.isNotBlank(reasoning)) {
                totalChunks = isReason ?
                        (int)(totalChunks * 0.7) : // reasoning 占 70%
                        (int)(totalChunks * 0.3);  // content 占 30%
            }

            // 计算每个分包的大致长度
            int chunkSize = Math.max(1, text.length() / totalChunks);

            // 分包
            int start = 0;
            while(start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                // 确保不会在单词中间截断
                if(end < text.length()) {
                    while(end > start && !Character.isWhitespace(text.charAt(end))) {
                        end--;
                    }
                    if(end == start) {
                        end = Math.min(start + chunkSize, text.length());
                    }
                }
                chunks.add(getChunk(text.substring(start, end), isReason && !property.isSplitReasoningFromContent()));
                start = end;
            }

            return chunks;
        }

        private StreamCompletionResponse getChunk(String text, boolean reasoning) {
            StreamCompletionResponse response = initStreamResponse(true);
            if(reasoning) {
                response.setReasoning(text);
            } else {
                response.setContent(text);
            }
            return response;
        }

        private StreamCompletionResponse getChunk(Message.ToolCall toolCall, boolean isName) {
            StreamCompletionResponse response = initStreamResponse(true);
            Message.ToolCall target = new Message.ToolCall();
            response.setToolCall(target);
            target.setType(target.getType());
            target.setId(toolCall.getId());
            target.setIndex(toolCall.getIndex());
            target.setFunction(new Message.FunctionCall());
            if(isName) {
                target.getFunction().setName(toolCall.getFunction().getName());
            } else {
                target.getFunction().setArguments(toolCall.getFunction().getArguments());
            }
            return response;
        }

        private StreamCompletionResponse getStopChunk() {
            StreamCompletionResponse response = initStreamResponse(true);
            response.setFinishReason(CollectionUtils.isNotEmpty(toolCalls) ? "tool_calls" : "stop");
            response.setContent("");
            return response;
        }

        private StreamCompletionResponse getUsageChunk() {
            StreamCompletionResponse response = initStreamResponse(false);
            response.setUsage(new CompletionResponse.TokenUsage());
            response.getUsage().setCompletion_tokens(100);
            response.getUsage().setPrompt_tokens(500);
            response.getUsage().setTotal_tokens(600);
            return response;
        }

        private StreamCompletionResponse initStreamResponse(boolean delta) {
            StreamCompletionResponse response = new StreamCompletionResponse();
            response.setId(id);
            response.setModel("mock-model");
            response.setCreated(Integer.parseInt(String.valueOf(System.currentTimeMillis() / 1000)));
            if(delta) {
                StreamCompletionResponse.Choice choice = new StreamCompletionResponse.Choice();
                response.setChoices(Lists.newArrayList(choice));
                choice.setIndex(0);
                Message message = new Message();
                message.setRole("assistant");
                choice.setDelta(message);
            } else {
                response.setChoices(Lists.newArrayList());
            }
            return response;
        }
    }
}

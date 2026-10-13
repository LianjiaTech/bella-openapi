package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.images.ImagesVariationRequest;
import com.ke.bella.openapi.protocol.images.editor.ImagesEditorAdaptor;
import com.ke.bella.openapi.protocol.images.generator.ImagesGeneratorAdaptor;
import com.ke.bella.openapi.protocol.images.variation.ImagesVariationAdaptor;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImagesControllerTest {
    private ImagesController controller;
    private ChannelRouter router;
    private final List<SafetyCheckRequest.Image> safetyRequests = new ArrayList<>();
    private TestGeneratorAdaptor generatorAdaptor;
    private TestEditorAdaptor editorAdaptor;
    private TestVariationAdaptor variationAdaptor;

    @BeforeEach
    void setUp() {
        controller = new ImagesController();
        router = mock(ChannelRouter.class);
        generatorAdaptor = new TestGeneratorAdaptor();
        editorAdaptor = new TestEditorAdaptor();
        variationAdaptor = new TestVariationAdaptor();
        AdaptorManager manager = AdaptorManager.getInstance();
        manager.register("/v1/images/generations", generatorAdaptor);
        manager.register("/v1/images/edits", editorAdaptor);
        manager.register("/v1/images/variations", variationAdaptor);

        ReflectionTestUtils.setField(controller, "router", router);
        ReflectionTestUtils.setField(controller, "adaptorManager", manager);
        ReflectionTestUtils.setField(controller, "limiterManager", mock(LimiterManager.class));
        ReflectionTestUtils.setField(controller, "endpointDataService", new EndpointDataService());
        ReflectionTestUtils.setField(controller, "imageSafetyCheckService",
                imageSafetyService((request, isMock) -> {
                    safetyRequests.add(request);
                    return null;
                }));
        EndpointContext.setApikey(ApikeyInfo.builder().apikey("ak").code("ak-code").build());
    }

    @AfterEach
    void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    void allImageEndpointsCheckInputThenOutput() {
        ImagesResponse response = imageResponse();
        generatorAdaptor.response = response;
        editorAdaptor.response = response;
        variationAdaptor.response = response;

        prepare("/v1/images/generations", generatorAdaptor.getClass().getSimpleName(), "sync");
        ImagesRequest generation = ImagesRequest.builder().model("model").prompt("prompt").build();
        assertSame(response, controller.generateImages(generation));
        assertRequestPair();
        assertSame(generation, safetyRequests.get(0).getOriginalRequest());
        assertSame(response, safetyRequests.get(1).getOriginalRequest());

        safetyRequests.clear();
        MockMultipartHttpServletRequest editRequest = new MockMultipartHttpServletRequest();
        editRequest.setRequestURI("/v1/images/edits");
        editRequest.addFile(new MockMultipartFile("image", "source.png", "image/png", new byte[] { 1 }));
        editRequest.setParameter("model", "model");
        editRequest.setParameter("prompt", "edit");
        prepare(editRequest, editorAdaptor.getClass().getSimpleName(), "sync");
        assertSame(response, controller.editImages(editRequest));
        assertRequestPair();
        assertEquals(ImagesEditRequest.class, safetyRequests.get(0).getOriginalRequest().getClass());
        assertSame(response, safetyRequests.get(1).getOriginalRequest());

        safetyRequests.clear();
        MockHttpServletRequest variationHttpRequest = new MockHttpServletRequest("POST", "/v1/images/variations");
        prepare(variationHttpRequest, variationAdaptor.getClass().getSimpleName(), "sync");
        ImagesVariationRequest variation = ImagesVariationRequest.builder().model("model")
                .image(new MockMultipartFile("image", "source.png", "image/png", new byte[] { 1 })).build();
        assertSame(response, controller.createVariations(variation));
        assertRequestPair();
        assertSame(variation, safetyRequests.get(0).getOriginalRequest());
        assertSame(response, safetyRequests.get(1).getOriginalRequest());
    }

    @Test
    void skipModeIsHandledByDelegator() {
        generatorAdaptor.response = imageResponse();
        prepare("/v1/images/generations", generatorAdaptor.getClass().getSimpleName(), "skip");

        ImagesResponse response = controller.generateImages(ImagesRequest.builder().model("model").prompt("prompt").build());

        assertSame(generatorAdaptor.response, response);
        assertEquals(1, generatorAdaptor.calls);
        assertEquals(0, safetyRequests.size());
    }

    @Test
    void syncInputBlockPreventsUpstreamCall() {
        prepare("/v1/images/generations", generatorAdaptor.getClass().getSimpleName(), "sync");
        ReflectionTestUtils.setField(controller, "imageSafetyCheckService",
                imageSafetyService((request, isMock) -> {
                    throw new BellaException.SafetyCheckException(Collections.singletonMap("blocked", true));
                }));

        assertThrows(BellaException.SafetyCheckException.class,
                () -> controller.generateImages(ImagesRequest.builder().model("model").prompt("blocked").build()));
        assertEquals(0, generatorAdaptor.calls);
    }

    @Test
    void syncOutputBlockRejectsWholeResponseAfterOneUpstreamCall() {
        generatorAdaptor.response = imageResponse();
        prepare("/v1/images/generations", generatorAdaptor.getClass().getSimpleName(), "sync");
        ReflectionTestUtils.setField(controller, "imageSafetyCheckService",
                imageSafetyService((request, isMock) -> {
                    if(!request.isRequest()) {
                        throw new BellaException.SafetyCheckException(Collections.singletonMap("blocked", true));
                    }
                    return null;
                }));

        assertThrows(BellaException.SafetyCheckException.class,
                () -> controller.generateImages(ImagesRequest.builder().model("model").prompt("prompt").build()));
        assertEquals(1, generatorAdaptor.calls);
    }

    @Test
    void asyncGenerationUsesPreparedSnapshotsAfterRequestClearAndResponseMutation() throws InterruptedException {
        ImagesResponse response = imageResponse();
        generatorAdaptor.response = response;
        generatorAdaptor.clearRequest = true;
        prepare("/v1/images/generations", generatorAdaptor.getClass().getSimpleName(), "async");
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        AtomicReference<String> checkedPrompt = new AtomicReference<>();
        AtomicReference<String> checkedUrl = new AtomicReference<>();
        ReflectionTestUtils.setField(controller, "imageSafetyCheckService", new ISafetyCheckService.IImageSafetyCheckService() {
            @Override
            public Object safetyCheck(SafetyCheckRequest.Image request, boolean isMock) {
                throw new AssertionError("async path should execute the prepared check");
            }

            @Override
            public Supplier<Object> prepareAsyncCheck(SafetyCheckRequest.Image request, boolean isMock) {
                if(request.isRequest()) {
                    String promptSnapshot = ((ImagesRequest) request.getOriginalRequest()).getPrompt();
                    return () -> {
                        await(release);
                        checkedPrompt.set(promptSnapshot);
                        completed.countDown();
                        return null;
                    };
                }
                String urlSnapshot = ((ImagesResponse) request.getOriginalRequest()).getData().get(0).getUrl();
                return () -> {
                    await(release);
                    checkedUrl.set(urlSnapshot);
                    completed.countDown();
                    return null;
                };
            }
        });
        ImagesRequest request = ImagesRequest.builder().model("model").prompt("original prompt").build();

        assertSame(response, controller.generateImages(request));
        assertNull(request.getPrompt());
        response.getData().get(0).setUrl("[REMOVED: image url]");
        release.countDown();

        assertTrue(completed.await(3, TimeUnit.SECONDS));
        assertEquals("original prompt", checkedPrompt.get());
        assertEquals("https://example.com/output.png", checkedUrl.get());
    }

    @Test
    void asyncEditPreparesMultipartBytesBeforeAdaptorClearsRequest() throws InterruptedException {
        editorAdaptor.response = imageResponse();
        editorAdaptor.clearRequest = true;
        MockMultipartHttpServletRequest editRequest = new MockMultipartHttpServletRequest();
        editRequest.setRequestURI("/v1/images/edits");
        editRequest.addFile(new MockMultipartFile("image", "source.png", "image/png", new byte[] { 1, 2, 3 }));
        editRequest.setParameter("model", "model");
        editRequest.setParameter("prompt", "edit");
        prepare(editRequest, editorAdaptor.getClass().getSimpleName(), "async");
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<byte[]> checkedBytes = new AtomicReference<>();
        ReflectionTestUtils.setField(controller, "imageSafetyCheckService", new ISafetyCheckService.IImageSafetyCheckService() {
            @Override
            public Object safetyCheck(SafetyCheckRequest.Image request, boolean isMock) {
                throw new AssertionError("async path should execute the prepared check");
            }

            @Override
            public Supplier<Object> prepareAsyncCheck(SafetyCheckRequest.Image request, boolean isMock) {
                if(!request.isRequest()) {
                    return () -> null;
                }
                ImagesEditRequest original = (ImagesEditRequest) request.getOriginalRequest();
                byte[] imageSnapshot;
                try {
                    imageSnapshot = original.getImage()[0].getBytes();
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
                return () -> {
                    await(release);
                    checkedBytes.set(imageSnapshot);
                    completed.countDown();
                    return null;
                };
            }
        });

        assertSame(editorAdaptor.response, controller.editImages(editRequest));
        assertTrue(editorAdaptor.requestCleared);
        release.countDown();

        assertTrue(completed.await(3, TimeUnit.SECONDS));
        assertArrayEquals(new byte[] { 1, 2, 3 }, checkedBytes.get());
    }

    private void assertRequestPair() {
        assertEquals(2, safetyRequests.size());
        assertEquals(true, safetyRequests.get(0).isRequest());
        assertEquals(false, safetyRequests.get(1).isRequest());
    }

    private void prepare(String endpoint, String protocol, String mode) {
        prepare(new MockHttpServletRequest("POST", endpoint), protocol, mode);
    }

    private void prepare(MockHttpServletRequest request, String protocol, String mode) {
        EndpointContext.setRequest(request);
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode("channel");
        channel.setEntityCode("model");
        channel.setUrl("https://example.com");
        channel.setProtocol(protocol);
        channel.setSupplier("supplier");
        channel.setVisibility("public");
        channel.setChannelInfo("{\"safetyCheckMode\":\"" + mode + "\"}");
        when(router.route(anyString(), anyString(), org.mockito.ArgumentMatchers.any(ApikeyInfo.class), anyBoolean())).thenReturn(channel);
    }

    private ImagesResponse imageResponse() {
        ImagesResponse.ImageData data = new ImagesResponse.ImageData();
        data.setUrl("https://example.com/output.png");
        ImagesResponse response = new ImagesResponse();
        response.setData(Collections.singletonList(data));
        return response;
    }

    private ISafetyCheckService.IImageSafetyCheckService imageSafetyService(
            BiFunction<SafetyCheckRequest.Image, Boolean, Object> check) {
        return new ISafetyCheckService.IImageSafetyCheckService() {
            @Override
            public Object safetyCheck(SafetyCheckRequest.Image request, boolean isMock) {
                return check.apply(request, isMock);
            }

            @Override
            public Supplier<Object> prepareAsyncCheck(SafetyCheckRequest.Image request, boolean isMock) {
                return () -> check.apply(request, isMock);
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static class TestGeneratorAdaptor implements ImagesGeneratorAdaptor<ImagesProperty> {
        private int calls;
        private ImagesResponse response;
        private boolean clearRequest;
        public String endpoint() { return "/v1/images/generations"; }
        public String getDescription() { return "test"; }
        public Class<?> getPropertyClass() { return ImagesProperty.class; }
        public ImagesResponse generateImages(ImagesRequest request, String url, ImagesProperty property) {
            calls++;
            if(clearRequest) {
                request.clearLargeData();
            }
            return response;
        }
    }

    private static class TestEditorAdaptor implements ImagesEditorAdaptor<ImagesEditorProperty> {
        private ImagesResponse response;
        private boolean clearRequest;
        private boolean requestCleared;
        public String endpoint() { return "/v1/images/edits"; }
        public String getDescription() { return "test"; }
        public Class<?> getPropertyClass() { return ImagesEditorProperty.class; }
        public ImagesResponse doEditImages(ImagesEditRequest request, String url, ImagesEditorProperty property, ImageDataType dataType)
                throws IOException {
            if(clearRequest) {
                request.clearLargeData();
                requestCleared = request.isCleared();
            }
            return response;
        }
    }

    private static class TestVariationAdaptor implements ImagesVariationAdaptor<ImagesProperty> {
        private ImagesResponse response;
        public String endpoint() { return "/v1/images/variations"; }
        public String getDescription() { return "test"; }
        public Class<?> getPropertyClass() { return ImagesProperty.class; }
        public ImagesResponse createVariations(ImagesVariationRequest request, String url, ImagesProperty property) { return response; }
    }
}

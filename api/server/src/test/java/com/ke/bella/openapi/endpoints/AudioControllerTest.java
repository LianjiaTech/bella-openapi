package com.ke.bella.openapi.endpoints;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.tts.TtsAdaptor;
import com.ke.bella.openapi.protocol.tts.TtsProperty;
import com.ke.bella.openapi.protocol.tts.TtsRequest;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;

public class AudioControllerTest {
    private static final String ENDPOINT = "/v1/audio/speech";
    private static final String MODEL = "tts-test";
    private static final byte[] AUDIO = new byte[] { 1, 2, 3 };

    private AudioController controller;
    private EndpointLogger logger;

    @Before
    public void setUp() {
        controller = new AudioController();
        logger = mock(EndpointLogger.class);

        TestTtsAdaptor adaptor = new TestTtsAdaptor();
        AdaptorManager.getInstance().register(ENDPOINT, adaptor);

        ReflectionTestUtils.setField(controller, "router", new TestChannelRouter(channel()));
        ReflectionTestUtils.setField(controller, "adaptorManager", AdaptorManager.getInstance());
        ReflectionTestUtils.setField(controller, "limiterManager", new NoopLimiterManager());
        ReflectionTestUtils.setField(controller, "logger", logger);
        ReflectionTestUtils.setField(controller, "endpointDataService", new EndpointDataService());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", ENDPOINT);
        EndpointContext.setRequest(request);
        EndpointContext.setApikey(ApikeyInfo.builder().apikey("ak").code("ak-code").build());
    }

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void speech_nonStreamingResponsePublishesEndpointLog() throws Exception {
        TtsRequest request = new TtsRequest();
        request.setModel(MODEL);
        request.setInput("test speech");
        request.setStream(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.speech(request, EndpointContext.getRequest(), response);

        assertArrayEquals(AUDIO, response.getContentAsByteArray());
        EndpointProcessData processData = EndpointContext.getProcessData();
        verify(logger, only()).log(processData);
    }

    private ChannelDB channel() {
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode("ch-tts-test");
        channel.setEntityCode(MODEL);
        channel.setUrl("http://tts.example.test/speech");
        channel.setProtocol(TestTtsAdaptor.class.getSimpleName());
        channel.setVisibility(EntityConstants.PUBLIC);
        channel.setChannelInfo("{}");
        channel.setPriceInfo("{\"input\":1}");
        return channel;
    }

    private static class TestChannelRouter extends ChannelRouter {
        private final ChannelDB channel;

        private TestChannelRouter(ChannelDB channel) {
            this.channel = channel;
        }

        @Override
        public ChannelDB route(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock) {
            return channel;
        }
    }

    private static class NoopLimiterManager extends LimiterManager {
        @Override
        public void incrementConcurrentCount(String akCode, String entityCode) {
        }
    }

    private static class TestTtsAdaptor implements TtsAdaptor<TtsProperty> {
        @Override
        public byte[] tts(TtsRequest request, String url, TtsProperty property) {
            return AUDIO;
        }

        @Override
        public void streamTts(TtsRequest request, String url, TtsProperty property, Callbacks.StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Callbacks.StreamCallback buildCallback(TtsRequest request, Callbacks.Sender byteSender,
                EndpointProcessData processData, EndpointLogger logger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDescription() {
            return "test tts adaptor";
        }

        @Override
        public Class<?> getPropertyClass() {
            return TtsProperty.class;
        }
    }
}

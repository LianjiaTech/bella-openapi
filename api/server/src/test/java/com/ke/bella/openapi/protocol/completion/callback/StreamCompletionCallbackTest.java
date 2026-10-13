package com.ke.bella.openapi.protocol.completion.callback;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.ISafetyResultStorage;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StreamCompletionCallbackTest {

    @Test
    public void safetyBlockSendsStandardErrorWithTopLevelSensitive() {
        CollectingStreamCompletionCallback callback = new CollectingStreamCompletionCallback();
        Map<String, Object> sensitive = new HashMap<>();
        sensitive.put("msg", "block");
        sensitive.put("trace_id", "trace_123");
        sensitive.put("results", new HashMap<>());

        callback.block(new BellaException.SafetyCheckException(sensitive));

        StreamCompletionResponse response = (StreamCompletionResponse) callback.events.get(0);
        assertEquals("content_filter", response.getChoices().get(0).getFinish_reason());
        assertEquals("内容未通过安全检查", response.getError().getMessage());
        assertEquals("safety_check", response.getError().getType());
        assertEquals("400", response.getError().getCode());
        assertEquals(Integer.valueOf(400), response.getError().getHttpCode());
        assertSame(sensitive, response.getSensitive());
        assertEquals("[DONE]", callback.events.get(1));
    }

    @Test
    public void doneDoesNotExposeResponseRiskDataOnSuccess() {
        Map<String, Object> riskData = new HashMap<>();
        riskData.put("msg", "warning");
        CollectingStreamCompletionCallback callback = new CollectingStreamCompletionCallback(new CollectingSafetyService(riskData));

        callback.done();

        assertEquals(1, callback.events.size());
        assertEquals("[DONE]", callback.events.get(0));
        assertTrue(callback.events.stream().noneMatch(event -> event instanceof StreamCompletionResponse
                && ((StreamCompletionResponse) event).getSensitive() != null));
    }

    private static class CollectingStreamCompletionCallback extends StreamCompletionCallback {
        private final List<Object> events = new ArrayList<>();

        CollectingStreamCompletionCallback() {
            this(null);
        }

        CollectingStreamCompletionCallback(ISafetyCheckService<SafetyCheckRequest.Chat> safetyService) {
            super(null, processData(), null, null, safetyService);
        }

        void block(BellaException.SafetyCheckException e) {
            handleBlock(e);
        }

        @Override
        public void send(Object data) {
            events.add(data);
        }

        private static EndpointProcessData processData() {
            EndpointProcessData processData = new EndpointProcessData();
            processData.setRequestId("req_123");
            return processData;
        }
    }

    private static class CollectingSafetyService implements ISafetyCheckService.IChatSafetyCheckService, ISafetyResultStorage {
        private final Object responseRiskData;

        CollectingSafetyService(Object responseRiskData) {
            this.responseRiskData = responseRiskData;
        }

        @Override
        public Object safetyCheck(SafetyCheckRequest.Chat request, boolean isMock) {
            return null;
        }

        @Override
        public void addRiskData(Object riskData, boolean isRequest) {
        }

        @Override
        public Object getRequestRiskData() {
            return null;
        }

        @Override
        public Object getResponseRiskData() {
            return responseRiskData;
        }
    }
}

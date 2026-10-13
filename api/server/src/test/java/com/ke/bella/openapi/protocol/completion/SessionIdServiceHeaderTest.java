package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.SessionIdService;
import okhttp3.Request;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionIdServiceHeaderTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void configuredHeaderReceivesSessionIdAndOverridesExtraHeader() {
        EndpointContext.getProcessData().setSessionId("resolved-session");
        OpenAIProperty property = property("X-Prompt-Cache-Id", "configured-id");

        Request outbound = inject(property);

        assertThat(outbound.headers("X-Prompt-Cache-Id")).containsExactly("resolved-session");
    }

    @Test
    public void arbitraryConfiguredHeaderNameIsSupported() {
        EndpointContext.getProcessData().setSessionId("resolved-session");
        OpenAIProperty property = property("X-Provider-Session", null);

        Request outbound = inject(property);

        assertThat(outbound.header("X-Provider-Session")).isEqualTo("resolved-session");
    }

    @Test
    public void missingHeaderConfigurationDoesNotAddDynamicHeader() {
        EndpointContext.getProcessData().setSessionId("resolved-session");
        OpenAIProperty property = property(null, "configured-id");

        Request outbound = inject(property);

        assertThat(outbound.headers("X-Prompt-Cache-Id")).containsExactly("configured-id");
    }

    @Test
    public void missingSessionIdKeepsStaticExtraHeader() {
        OpenAIProperty property = property("X-Prompt-Cache-Id", "configured-id");

        Request outbound = inject(property);

        assertThat(outbound.headers("X-Prompt-Cache-Id")).containsExactly("configured-id");
    }

    private OpenAIProperty property(String sessionIdHeader, String extraHeaderValue) {
        OpenAIProperty property = new OpenAIProperty();
        property.setSessionId(sessionIdHeader);
        if(extraHeaderValue != null) {
            property.setExtraHeaders(Collections.singletonMap("X-Prompt-Cache-Id", extraHeaderValue));
        }
        return property;
    }

    private Request inject(OpenAIProperty property) {
        Request.Builder builder = new Request.Builder().url("http://localhost/test");
        if(property.getExtraHeaders() != null) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }
        SessionIdService.inject(builder, property);
        return builder.build();
    }
}

package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.space.CreateSpaceOp;
import org.junit.After;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class BellaApiRequestAdviceTest {

    @After
    public void clearContext() {
        BellaContext.clearAll();
    }

    @Test
    public void fillsMissingOperatorIdFromAuthenticatedApiKey() {
        BellaApiRequestAdvice advice = new BellaApiRequestAdvice();
        ReflectionTestUtils.setField(advice, "profile", "prod");
        BellaContext.setApikey(ApikeyInfo.builder().userId(200L).ownerCode("200").build());
        CreateSpaceOp request = CreateSpaceOp.builder().spaceName("space").build();

        advice.afterBodyRead(request, null, null, null, null);

        assertThat(request.getUserId()).isEqualTo(200L);
        assertThat(BellaContext.getOperator().getUserId()).isEqualTo(200L);
    }

    @Test
    public void keepsExplicitOperatorId() {
        BellaApiRequestAdvice advice = new BellaApiRequestAdvice();
        ReflectionTestUtils.setField(advice, "profile", "prod");
        BellaContext.setApikey(ApikeyInfo.builder().userId(200L).build());
        CreateSpaceOp request = CreateSpaceOp.builder().userId(300L).spaceName("space").build();

        advice.afterBodyRead(request, null, null, null, null);

        assertThat(request.getUserId()).isEqualTo(300L);
    }
}

package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.protocol.AdaptorManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RerankEndpointMetadataTest {
    @Test
    void adaptorManagerSupportsAliRerankProtocol() {
        AdaptorManager manager = AdaptorManager.getInstance();
        manager.register("/v1/reranks", new AliRerankAdaptor());

        assertTrue(manager.support("/v1/reranks", "AliRerankAdaptor"));
    }

    @Test
    void systemEndpointMatchesRerank() {
        assertEquals("/v*/reranks", EntityConstants.SystemBasicEndpoint.RERANK_ENDPOINT.getEndpoint());
        assertEquals(EntityConstants.SystemBasicCategory.RERANK, EntityConstants.SystemBasicEndpoint.RERANK_ENDPOINT.getCategory());
        assertEquals("0009", EntityConstants.SystemBasicEndpoint.RERANK_ENDPOINT.getCategory().getCode());
    }
}

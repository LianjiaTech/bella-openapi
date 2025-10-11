package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.embedding.EmbeddingAdaptor;
import com.ke.bella.openapi.protocol.embedding.EmbeddingProperty;
import com.ke.bella.openapi.protocol.embedding.EmbeddingRequest;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EndpointAPI
@RestController
@RequestMapping("/v1/embeddings")
@Tag(name = "chat")
public class EmbeddingController {
    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointDataService endpointDataService;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping
    public Object embedding(@RequestBody EmbeddingRequest request) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request);
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        EmbeddingAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, EmbeddingAdaptor.class);
        EmbeddingProperty property = (EmbeddingProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());
        return adaptor.embedding(request, url, property);
    }
}

package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.systemone.SystemOneAdaptor;
import com.ke.bella.openapi.protocol.systemone.SystemOneProperty;
import com.ke.bella.openapi.protocol.systemone.SystemOneRequest;
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
@RequestMapping("/v1/systemone")
@Tag(name = "chat")
public class SystemOneController {
    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointDataService endpointDataService;

    @PostMapping
    public Object evaluate(@RequestBody SystemOneRequest request) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request);
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);
        if(!processData.isPrivate()) {
            limiterManager.incrementConcurrentCount(processData.getAkCode(), model);
        }
        SystemOneAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, processData.getProtocol(), SystemOneAdaptor.class);
        SystemOneProperty property = (SystemOneProperty) JacksonUtils.deserialize(channel.getChannelInfo(), adaptor.getPropertyClass());
        return adaptor.evaluate(request, processData.getForwardUrl(), property);
    }
}

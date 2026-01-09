package com.ke.bella.openapi.protocol.ocr;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OcrContext {
    private final ChannelDB channel;
    private final String endpoint;
    private final String url;
    private final String protocol;
    private final String channelInfo;

    public static OcrContext initialize(
            OcrRequest request,
            EndpointDataService endpointDataService,
            ChannelRouter router,
            LimiterManager limiterManager) {

        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();

        endpointDataService.setEndpointData(endpoint, model, request.summary());
        EndpointProcessData processData = EndpointContext.getProcessData();

        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);

        if(!processData.isPrivate()) {
            limiterManager.incrementConcurrentCount(processData.getAkCode(), model);
        }

        return new OcrContext(
                channel,
                endpoint,
                processData.getForwardUrl(),
                processData.getProtocol(),
                channel.getChannelInfo());
    }
}

package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.alpha.search.AlphaSearchException;
import com.ke.bella.openapi.protocol.alpha.search.AlphaSearchExecutionContext;
import com.ke.bella.openapi.protocol.alpha.search.AlphaSearchProtocolAdaptor;
import com.ke.bella.openapi.protocol.alpha.search.AlphaSearchRequest;
import com.ke.bella.openapi.protocol.alpha.search.AlphaSearchResponse;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@EndpointAPI
@RestController
@RequestMapping("/v1/alpha")
@Tag(name = "alpha-search")
@RequiredArgsConstructor
public class AlphaSearchController {
    private final ChannelRouter router;
    private final AdaptorManager adaptorManager;
    private final LimiterManager limiterManager;
    private final EndpointDataService endpointDataService;
    private final EndpointLogger logger;

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Alpha Search")
    public void search(@RequestBody byte[] body, HttpServletResponse response) throws IOException {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        AlphaSearchRequest request = AlphaSearchRequest.parse(body);
        String modelName = request.getModel();
        endpointDataService.setEndpointData(endpoint, modelName, request.asJson());
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB channel = router.route(endpoint, modelName, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);
        if(!processData.isPrivate()) limiterManager.incrementConcurrentCount(processData.getAkCode(), modelName);

        com.ke.bella.openapi.protocol.IProtocolAdaptor protocolAdaptor =
                adaptorManager.getProtocolAdaptor(endpoint, processData.getProtocol());
        if(!(protocolAdaptor instanceof AlphaSearchProtocolAdaptor)) {
            throw new BizParamCheckException("Unsupported Alpha Search protocol: " + processData.getProtocol());
        }
        AlphaSearchProtocolAdaptor adaptor = (AlphaSearchProtocolAdaptor) protocolAdaptor;
        Object property = JacksonUtils.deserialize(channel.getChannelInfo(), adaptor.getPropertyClass());
        AlphaSearchResponse result;
        try {
            result = adaptor.search(request, new AlphaSearchExecutionContext(
                    processData.getForwardUrl(), processData.getDeployName(), property));
        } catch (AlphaSearchException e) {
            processData.setFailureStage(e.getFailureStage());
            throw e;
        }

        response.setStatus(result.getStatus());
        if(result.getContentType() != null) response.setContentType(result.getContentType());
        response.getOutputStream().write(result.getBody());
        response.getOutputStream().flush();
        processData.setResponse(new OpenapiResponse());
        processData.setResponseRaw(result.bodyAsString());
        processData.setUsage(result.getUsage());
        logger.log(processData);
    }
}

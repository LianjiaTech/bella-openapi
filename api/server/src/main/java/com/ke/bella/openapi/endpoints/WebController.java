package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.web.WebCrawlAdaptor;
import com.ke.bella.openapi.protocol.web.WebCrawlProperty;
import com.ke.bella.openapi.protocol.web.WebCrawlRequest;
import com.ke.bella.openapi.protocol.web.WebCrawlResponse;
import com.ke.bella.openapi.protocol.web.WebExtractAdaptor;
import com.ke.bella.openapi.protocol.web.WebExtractProperty;
import com.ke.bella.openapi.protocol.web.WebExtractRequest;
import com.ke.bella.openapi.protocol.web.WebExtractResponse;
import com.ke.bella.openapi.protocol.web.WebSearchAdaptor;
import com.ke.bella.openapi.protocol.web.WebSearchProperty;
import com.ke.bella.openapi.protocol.web.WebSearchRequest;
import com.ke.bella.openapi.protocol.web.WebSearchResponse;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web Search Controller Provides endpoints for web search and crawl
 * functionality using Tavily API
 */
@EndpointAPI
@RestController
@RequestMapping("/v1/web")
@Tag(name = "web")
@Slf4j
public class WebController {

    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointDataService endpointDataService;

    /**
     * Web Search endpoint Performs web search using Tavily Search API
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/search")
    @Operation(summary = "Web Search", description = "Search the web using Tavily Search API")
    public WebSearchResponse webSearch(@RequestBody WebSearchRequest request) {
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

        WebSearchAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, WebSearchAdaptor.class);
        WebSearchProperty property = (WebSearchProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());

        return adaptor.search(request, url, property);
    }

    /**
     * Web Crawl endpoint Performs web crawling using Tavily Crawl API
     */
    @PostMapping("/crawl")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public WebCrawlResponse webCrawl(@RequestBody WebCrawlRequest request) {
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

        WebCrawlAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, WebCrawlAdaptor.class);
        WebCrawlProperty property = (WebCrawlProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());

        return adaptor.crawl(request, url, property);
    }

    /**
     * Web Extract endpoint
     * Extract web page content from one or more specified URLs using Tavily
     * Extract API
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/extract")
    public WebExtractResponse webExtract(@RequestBody WebExtractRequest request) {
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

        WebExtractAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, WebExtractAdaptor.class);
        WebExtractProperty property = (WebExtractProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());

        return adaptor.extract(request, url, property);
    }
}

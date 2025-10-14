package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.images.generator.ImagesGeneratorAdaptor;
import com.ke.bella.openapi.protocol.images.editor.ImagesEditorAdaptor;
import com.ke.bella.openapi.protocol.images.variation.ImagesVariationAdaptor;
import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesVariationRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.ImagesEditRequestUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;

@EndpointAPI
@RestController
@RequestMapping("/v1/images")
@Tag(name = "images")
@Slf4j
public class ImagesController {
    
    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointDataService endpointDataService;
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/generations")
    public ImagesResponse generateImages(@RequestBody ImagesRequest request) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request);
        boolean isMock = EndpointContext.getProcessData().isMock();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), isMock);
        endpointDataService.setChannel(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        ImagesGeneratorAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, ImagesGeneratorAdaptor.class);
        ImagesProperty property = (ImagesProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        return adaptor.generateImages(request, url, property);
    }
    
    /**
     * 图片编辑接口 - 使用工具类处理multipart请求
     * @param servletRequest HTTP请求对象
     * @return 编辑后的图片响应
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/edits")
    public ImagesResponse editImages(HttpServletRequest servletRequest) {
        // 使用工具类处理multipart请求
        if (!(servletRequest instanceof MultipartHttpServletRequest)) {
            throw new IllegalArgumentException("Request must be multipart/form-data");
        }

        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) servletRequest;
        ImagesEditRequest request = ImagesEditRequestUtils.readFromMultipartRequest(multipartRequest);
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request.summary());
        boolean isMock = EndpointContext.getProcessData().isMock();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), isMock);
        endpointDataService.setChannel(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        ImagesEditorAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, ImagesEditorAdaptor.class);
        ImagesEditorProperty property = (ImagesEditorProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        return adaptor.editImages(request, url, property);
    }
    
    /**
     * 图片变化接口
     * @param request 图片变化请求参数
     * @return 变化后的图片响应
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/variations")
    public ImagesResponse createVariations(ImagesVariationRequest request) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request.summary());
        boolean isMock = EndpointContext.getProcessData().isMock();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), isMock);
        endpointDataService.setChannel(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        ImagesVariationAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, ImagesVariationAdaptor.class);
        ImagesProperty property = (ImagesProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        return adaptor.createVariations(request, url, property);
    }

}

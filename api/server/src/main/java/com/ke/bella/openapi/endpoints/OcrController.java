package com.ke.bella.openapi.endpoints;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.ocr.OcrBankcardAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrIdcardAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.bankcard.OcrBankcardRequest;
import com.ke.bella.openapi.protocol.ocr.idcard.OcrIdcardRequest;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OCR识别控制器
 */
@EndpointAPI
@RestController
@RequestMapping("/v1/ocr")
@Tag(name = "ocr")
public class OcrController {
    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointDataService endpointDataService;
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/idcard")
    public Object idcard(@RequestBody OcrIdcardRequest request) {
        // 1. 设置请求上下文
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request);
        EndpointProcessData processData = EndpointContext.getProcessData();

        // 2. 参数校验
        validateRequest(request);

        // 3. 渠道路由选择
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);

        // 4. 并发限制管理
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }

        // 5. 获取协议适配器
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        OcrIdcardAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, OcrIdcardAdaptor.class);
        OcrProperty property = (OcrProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());

        // 6. 调用适配器处理
        return adaptor.idcard(request, url, property);
    }

    @PostMapping("/bankcard")
    public Object bankcard(@RequestBody OcrBankcardRequest request) {
        // 1. 设置请求上下文
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request.summary());
        EndpointProcessData processData = EndpointContext.getProcessData();

        // 2. 参数校验
        validateRequest(request);

        // 3. 渠道路由选择
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);

        // 4. 并发限制管理
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }

        // 5. 获取协议适配器
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        OcrBankcardAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, OcrBankcardAdaptor.class);
        OcrProperty property = (OcrProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());

        // 6. 调用适配器处理
        return adaptor.bankcard(request, url, property);
    }

    private void validateRequest(OcrRequest request) {
        // 校验模型参数
        Assert.hasText(request.getModel(), "model参数不能为空");

        // 校验图片输入：三选一
        int imageInputCount = 0;
        if(StringUtils.hasText(request.getImageBase64()))
            imageInputCount++;
        if(StringUtils.hasText(request.getImageUrl()))
            imageInputCount++;
        if(StringUtils.hasText(request.getFileId()))
            imageInputCount++;

        Assert.isTrue(imageInputCount == 1, "image_base64、image_url、file_id必须三选一");
    }
}

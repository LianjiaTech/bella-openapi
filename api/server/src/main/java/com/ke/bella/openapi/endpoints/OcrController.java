package com.ke.bella.openapi.endpoints;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.bankcard.BankcardAdaptor;
import com.ke.bella.openapi.protocol.ocr.general.GeneralAdaptor;
import com.ke.bella.openapi.protocol.ocr.idcard.IdcardAdaptor;
import com.ke.bella.openapi.protocol.ocr.residence_permit.ResidencePermitAdaptor;
import com.ke.bella.openapi.protocol.ocr.tmp_idcard.TmpIdcardAdaptor;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;

import io.swagger.v3.oas.annotations.tags.Tag;

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

    private static class OcrChannelContext<T extends IProtocolAdaptor> {
        String url;
        T adaptor;
        OcrProperty property;
    }

    private <T extends IProtocolAdaptor> OcrChannelContext<T> initializeOcrChannel(
            OcrRequest request,
            Class<T> adaptorClass) {

        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();

        endpointDataService.setEndpointData(endpoint, model, request.summary());
        EndpointProcessData processData = EndpointContext.getProcessData();

        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);

        if(!processData.isPrivate()) {
            limiterManager.incrementConcurrentCount(processData.getAkCode(), model);
        }

        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        T adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, adaptorClass);
        OcrProperty property = (OcrProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());

        EndpointContext.setEncodingType(property.getEncodingType());

        OcrChannelContext<T> ctx = new OcrChannelContext<>();
        ctx.url = url;
        ctx.adaptor = adaptor;
        ctx.property = property;
        return ctx;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/idcard")
    public Object idcard(@RequestBody @Valid OcrRequest request) {
        OcrChannelContext<IdcardAdaptor> ctx = initializeOcrChannel(request, IdcardAdaptor.class);
        return ctx.adaptor.idcard(request, ctx.url, ctx.property);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/bankcard")
    public Object bankcard(@RequestBody @Valid OcrRequest request) {
        OcrChannelContext<BankcardAdaptor> ctx = initializeOcrChannel(request, BankcardAdaptor.class);
        return ctx.adaptor.bankcard(request, ctx.url, ctx.property);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/hmt-residence-permit")
    public Object hmtResidencePermit(@RequestBody @Valid OcrRequest request) {
        OcrChannelContext<ResidencePermitAdaptor> ctx = initializeOcrChannel(request, ResidencePermitAdaptor.class);
        return ctx.adaptor.hmtResidencePermit(request, ctx.url, ctx.property);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/tmp-idcard")
    public Object tmpIdcard(@RequestBody @Valid OcrRequest request) {
        OcrChannelContext<TmpIdcardAdaptor> ctx = initializeOcrChannel(request, TmpIdcardAdaptor.class);
        return ctx.adaptor.tmpIdcard(request, ctx.url, ctx.property);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/general")
    public Object general(@RequestBody @Valid OcrRequest request) {
        OcrChannelContext<GeneralAdaptor> ctx = initializeOcrChannel(request, GeneralAdaptor.class);
        return ctx.adaptor.general(request, ctx.url, ctx.property);
    }

}

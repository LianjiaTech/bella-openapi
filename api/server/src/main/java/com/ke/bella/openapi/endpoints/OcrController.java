package com.ke.bella.openapi.endpoints;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.ocr.OcrContext;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.bankcard.BankcardAdaptor;
import com.ke.bella.openapi.protocol.ocr.general.GeneralAdaptor;
import com.ke.bella.openapi.protocol.ocr.idcard.IdcardAdaptor;
import com.ke.bella.openapi.protocol.ocr.residence_permit.ResidencePermitAdaptor;
import com.ke.bella.openapi.protocol.ocr.tmp_idcard.TmpIdcardAdaptor;
import com.ke.bella.openapi.service.EndpointDataService;
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/idcard")
    public Object idcard(@RequestBody @Valid OcrRequest request) {
        OcrContext ctx = OcrContext.initialize(request, endpointDataService, router, limiterManager);

        IdcardAdaptor adaptor = adaptorManager.getProtocolAdaptor(ctx.getEndpoint(), ctx.getProtocol(), IdcardAdaptor.class);
        OcrProperty property = (OcrProperty) JacksonUtils.deserialize(ctx.getChannelInfo(), adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());

        return adaptor.idcard(request, ctx.getUrl(), property);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/bankcard")
    public Object bankcard(@RequestBody @Valid OcrRequest request) {
        OcrContext ctx = OcrContext.initialize(request, endpointDataService, router, limiterManager);

        BankcardAdaptor adaptor = adaptorManager.getProtocolAdaptor(ctx.getEndpoint(), ctx.getProtocol(), BankcardAdaptor.class);
        OcrProperty property = (OcrProperty) JacksonUtils.deserialize(ctx.getChannelInfo(), adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());

        return adaptor.bankcard(request, ctx.getUrl(), property);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/hmt-residence-permit")
    public Object hmtResidencePermit(@RequestBody @Valid OcrRequest request) {
        OcrContext ctx = OcrContext.initialize(request, endpointDataService, router, limiterManager);

        ResidencePermitAdaptor adaptor = adaptorManager.getProtocolAdaptor(ctx.getEndpoint(), ctx.getProtocol(), ResidencePermitAdaptor.class);
        OcrProperty property = (OcrProperty) JacksonUtils.deserialize(ctx.getChannelInfo(), adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());

        return adaptor.hmtResidencePermit(request, ctx.getUrl(), property);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/tmp-idcard")
    public Object tmpIdcard(@RequestBody @Valid OcrRequest request) {
        OcrContext ctx = OcrContext.initialize(request, endpointDataService, router, limiterManager);

        TmpIdcardAdaptor adaptor = adaptorManager.getProtocolAdaptor(ctx.getEndpoint(), ctx.getProtocol(), TmpIdcardAdaptor.class);
        OcrProperty property = (OcrProperty) JacksonUtils.deserialize(ctx.getChannelInfo(), adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());

        return adaptor.tmpIdcard(request, ctx.getUrl(), property);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/general")
    public Object general(@RequestBody @Valid OcrRequest request) {
        OcrContext ctx = OcrContext.initialize(request, endpointDataService, router, limiterManager);

        GeneralAdaptor adaptor = adaptorManager.getProtocolAdaptor(ctx.getEndpoint(), ctx.getProtocol(), GeneralAdaptor.class);
        OcrProperty property = (OcrProperty) JacksonUtils.deserialize(ctx.getChannelInfo(), adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());

        return adaptor.general(request, ctx.getUrl(), property);
    }

}

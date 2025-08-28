package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.route.RouteRequest;
import com.ke.bella.openapi.protocol.route.RouteResult;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.EncryptUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.openapi.annotations.BellaAPI;

import io.swagger.v3.oas.annotations.tags.Tag;

@BellaAPI
@RestController
@RequestMapping("/v1/route")
@Tag(name = "路由")
public class RouteController {
    @Autowired
    private ChannelRouter channelRouter;
    @Autowired
    private ApikeyService apikeyService;

    @PostMapping
    public RouteResult route(@RequestBody RouteRequest request) {
        String sha = EncryptUtils.sha256(request.getApikey());
        ApikeyInfo apikeyInfo = apikeyService.queryBySha(sha, true);
        if(apikeyInfo == null) {
            throw new BizParamCheckException("用户的Apikey不存在");
        }

        ChannelDB channelDB = channelRouter.route(request.getEndpoint(), request.getModel(), apikeyInfo, request.getQueueMode());

        return RouteResult.builder()
                .channelCode(channelDB.getChannelCode())
                .entityType(channelDB.getEntityType())
                .entityCode(channelDB.getEntityCode())
                .protocol(channelDB.getProtocol())
                .url(channelDB.getUrl())
                .channelInfo(JacksonUtils.toMap(channelDB.getChannelInfo()))
                .priceInfo(channelDB.getPriceInfo())
                .queueMode(channelDB.getQueueMode().intValue())
                .queueName(channelDB.getQueueName())
                .build();
    }

}

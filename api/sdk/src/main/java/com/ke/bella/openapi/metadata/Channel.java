package com.ke.bella.openapi.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.BaseDto;
import com.ke.bella.openapi.protocol.IPriceInfo;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Channel extends BaseDto {
    private String entityType;
    private String entityCode;
    private String channelCode;
    private String status;
    private String dataDestination;
    private String priority;
    private String protocol;
    private String supplier;
    private String url;
    private String channelInfo;
    private String priceInfo;
    private Byte trialEnabled;
    private Integer queueMode;
    private String queueName;

    public <T extends IProtocolProperty> T toChannelInfo(Class<T> type) {
        return JacksonUtils.deserialize(channelInfo, type);
    }

    public <T extends IPriceInfo> T toPriceInfo(Class<T> type) {
        return JacksonUtils.deserialize(priceInfo, type);
    }
}

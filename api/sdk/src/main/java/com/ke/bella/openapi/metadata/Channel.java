package com.ke.bella.openapi.metadata;

import com.ke.bella.openapi.protocol.IPriceInfo;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Channel {
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
    private Long cuid;
    private String cuName;
    private Long muid;
    private String muName;
    private LocalDateTime ctime;
    private LocalDateTime mtime;

    public <T extends IProtocolProperty> T toChannelInfo(Class<T> type) {
        return JacksonUtils.deserialize(channelInfo, type);
    }

    public <T extends IPriceInfo> T toPriceInfo(Class<T> type) {
        return JacksonUtils.deserialize(priceInfo, type);
    }
}

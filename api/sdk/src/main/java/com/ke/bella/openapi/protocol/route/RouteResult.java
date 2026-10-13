package com.ke.bella.openapi.protocol.route;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class RouteResult {
    private String channelCode;
    private String entityType;
    private String entityCode;
    private String protocol;
    private String url;
    private Object channelInfo;
    private String priceInfo;
    private Integer queueMode;
    private String queueName;
}

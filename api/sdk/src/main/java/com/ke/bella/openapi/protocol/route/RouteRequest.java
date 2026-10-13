package com.ke.bella.openapi.protocol.route;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RouteRequest {
    private String apikey;
    private String endpoint;
    private String model;
    private Integer queueMode;
}

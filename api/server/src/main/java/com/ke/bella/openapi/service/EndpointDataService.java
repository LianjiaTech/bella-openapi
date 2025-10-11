package com.ke.bella.openapi.service;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Endpoint数据服务 - 包装EndpointContext调用以支持AOP拦截
 */
@Slf4j
@Service
public class EndpointDataService {

    /**
     * 设置endpoint数据（包含request）
     * 此方法会被AOP拦截以触发请求优化
     */
    public void setEndpointData(String endpoint, String model, ChannelDB channel, Object request) {
        EndpointContext.setEndpointData(endpoint, model, channel, request);
    }

    /**
     * 设置endpoint数据（包含request）
     * 此方法会被AOP拦截以触发请求优化
     */
    public void setEndpointData(String endpoint, String model, Object request) {
        EndpointContext.setEndpointData(endpoint, model, request);
    }

    /**
     * 设置channel数据
     * 无需AOP拦截
     */
    public void setChannel(ChannelDB channel) {
        EndpointContext.setEndpointData(channel);
    }

    /**
     * 设置endpoint数据（包含request）
     * 此方法会被AOP拦截以触发请求优化
     */
    public void setEndpointData(String endpoint, ChannelDB channel, Object request) {
        EndpointContext.setEndpointData(endpoint, channel, request);
    }
}
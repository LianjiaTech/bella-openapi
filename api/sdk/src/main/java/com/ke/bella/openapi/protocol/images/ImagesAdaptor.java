package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;

/**
 * 文生图适配器接口
 */
public interface ImagesAdaptor<T extends ImagesProperty> extends IProtocolAdaptor {
    
    /**
     * 生成图片
     * @param request 请求参数
     * @param url 请求地址
     * @param property 属性配置
     * @return 响应结果
     */
    ImagesResponse generateImages(ImagesRequest request, String url, T property);
}

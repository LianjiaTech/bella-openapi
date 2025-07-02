package com.ke.bella.openapi.protocol.images.variation;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesVariationRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;

/**
 * 图片变化适配器接口
 */
public interface ImagesVariationAdaptor<T extends ImagesProperty> extends IProtocolAdaptor {
    
    /**
     * 生成图片变化
     * @param request 请求参数
     * @param url 请求地址
     * @param property 属性配置
     * @return 响应结果
     */
    ImagesResponse createVariations(ImagesVariationRequest request, String url, T property);
}

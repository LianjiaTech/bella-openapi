package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;

/**
 * 图片编辑适配器接口
 */
public interface ImagesEditorAdaptor<T extends ImagesEditorProperty> extends IProtocolAdaptor {
    
    /**
     * 编辑图片
     * @param request 请求参数
     * @param url 请求地址
     * @param property 属性配置
     * @return 响应结果
     */
    ImagesResponse editImages(ImagesEditRequest request, String url, T property);
}

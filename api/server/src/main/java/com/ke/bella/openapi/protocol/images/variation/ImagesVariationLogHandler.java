package com.ke.bella.openapi.protocol.images.variation;

import com.ke.bella.openapi.protocol.images.ImagesLogHandler;
import org.springframework.stereotype.Component;

/**
 * 图片变化接口的日志处理器
 */
@Component
public class ImagesVariationLogHandler extends ImagesLogHandler {
    
    @Override
    public String endpoint() {
        return "/v1/images/variations";
    }
}

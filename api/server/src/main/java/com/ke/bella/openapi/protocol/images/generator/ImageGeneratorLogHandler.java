package com.ke.bella.openapi.protocol.images.generator;

import com.ke.bella.openapi.protocol.images.ImagesLogHandler;
import org.springframework.stereotype.Component;

/**
 * 图片生成接口的日志处理器
 */
@Component
public class ImageGeneratorLogHandler extends ImagesLogHandler {

    @Override
    public String endpoint() {
        return "/v1/images/generations";
    }
}

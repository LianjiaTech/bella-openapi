package com.ke.bella.openapi.protocol.ocr;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageData {
    private String imageBase64;
    private String imageUrl;
    private String fileId;
    private byte[] imageBinary;
    private ImageDataType imageType;
}

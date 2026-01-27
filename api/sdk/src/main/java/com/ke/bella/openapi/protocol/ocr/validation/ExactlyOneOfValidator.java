package com.ke.bella.openapi.protocol.ocr.validation;

import java.util.Map;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.ocr.OcrRequest;

/**
 * Validator implementation for {@link ExactlyOneOf} annotation.
 * Ensures that exactly one of the four image input fields is provided:
 * imageBase64, imageUrl, fileId, or file (in extra_body)
 */
public class ExactlyOneOfValidator implements ConstraintValidator<ExactlyOneOf, OcrRequest> {

    @Override
    public boolean isValid(OcrRequest request, ConstraintValidatorContext context) {
        if(request == null) {
            return true; // Null checks should be handled by @NotNull if needed
        }

        int imageInputCount = 0;

        if(StringUtils.hasText(request.getImageBase64())) {
            imageInputCount++;
        }
        if(StringUtils.hasText(request.getImageUrl())) {
            imageInputCount++;
        }
        if(StringUtils.hasText(request.getFileId())) {
            imageInputCount++;
        }

        // 检查 extra_body 中的 file 字段
        Map<String, Object> extraBody = request.getExtra_body();
        if(extraBody != null && extraBody.containsKey("file")) {
            Object file = extraBody.get("file");
            if(file != null && StringUtils.hasText(String.valueOf(file))) {
                imageInputCount++;
            }
        }

        return imageInputCount == 1;
    }
}

package com.ke.bella.openapi.protocol.ocr.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.ocr.OcrRequest;

/**
 * Validator implementation for {@link ExactlyOneOf} annotation.
 * Ensures that exactly one of the three image input fields (imageBase64,
 * imageUrl, fileId) is provided.
 */
public class ExactlyOneOfValidator implements ConstraintValidator<ExactlyOneOf, OcrRequest> {

    @Override
    public void initialize(ExactlyOneOf constraintAnnotation) {
    }

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

        return imageInputCount == 1;
    }
}

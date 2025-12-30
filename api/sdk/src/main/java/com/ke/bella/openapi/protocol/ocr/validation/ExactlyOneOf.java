package com.ke.bella.openapi.protocol.ocr.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * Custom validation annotation that ensures exactly one of three image input fields is provided.
 * Used for OCR requests where image_base64, image_url, or file_id must be specified (but only one).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExactlyOneOfValidator.class)
@Documented
public @interface ExactlyOneOf {

    String message() default "image_base64、image_url、file_id必须三选一";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

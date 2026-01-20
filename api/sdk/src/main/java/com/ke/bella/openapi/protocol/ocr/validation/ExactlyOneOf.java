package com.ke.bella.openapi.protocol.ocr.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * Custom validation annotation that ensures exactly one of four image input
 * fields is provided.
 * Used for OCR requests where image_base64, image_url, file_id, or file (in extra_body)
 * must be specified (but only one).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExactlyOneOfValidator.class)
@Documented
public @interface ExactlyOneOf {

    String message() default "image_base64、image_url、file_id、file（在extra_body中）必须四选一";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

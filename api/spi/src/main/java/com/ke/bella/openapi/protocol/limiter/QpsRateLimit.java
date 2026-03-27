package com.ke.bella.openapi.protocol.limiter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * QPS 限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QpsRateLimit {

    /**
     * 请求体中的字段名：
     * - 为空：无条件限流
     * - 非空且 values 为空：字段存在（非 null）即限流
     * - 非空且 values 非空：字段值命中 values 中任一值时限流
     */
    String field() default "";

    /**
     * 字段值白名单，命中其中任一值时才限流；为空时字段存在即限流
     */
    String[] values() default {};
}

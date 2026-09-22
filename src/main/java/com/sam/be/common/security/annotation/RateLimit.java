package com.sam.be.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Annotation áp dụng AOP Rate Limiting theo thuật toán Token Bucket. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    long limit() default 10;

    long duration() default 60; // Chu kỳ hồi phục tính bằng giây

    Type type() default Type.IP_ADDRESS;

    String fieldName() default "";

    enum Type {
        USER_ID,
        IP_ADDRESS,
        REQUEST_FIELD
    }
}

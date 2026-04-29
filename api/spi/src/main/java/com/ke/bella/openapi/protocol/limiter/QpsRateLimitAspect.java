package com.ke.bella.openapi.protocol.limiter;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.limiter.manager.QpsLimiterManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QPS 限流切面
 */
@Aspect
public class QpsRateLimitAspect {

    private static final int MAX_FIELD_CACHE_SIZE = 1000;

    private final QpsLimiterManager qpsLimiterManager;
    private final ConcurrentHashMap<String, Optional<Field>> fieldCache = new ConcurrentHashMap<>();

    public QpsRateLimitAspect(QpsLimiterManager qpsLimiterManager) {
        this.qpsLimiterManager = qpsLimiterManager;
    }

    @Around("@annotation(qpsRateLimit)")
    public Object checkQpsLimit(ProceedingJoinPoint pjp, QpsRateLimit qpsRateLimit) throws Throwable {
        if(shouldLimit(pjp.getArgs(), qpsRateLimit, pjp)) {
            ApikeyInfo apikey = BellaContext.getApikeyIgnoreNull();
            if(apikey != null) {
                QpsCheckResult result = qpsLimiterManager.checkLimit(apikey.getCode(), apikey.getQpsLimit());
                if(!result.isAllowed()) {
                    setRetryAfterHeader();
                    throw new BellaException.RateLimitException(
                            String.format("QPS 超过限制（当前: %d, 限制: %d），请 1 秒后重试",
                                    result.getCurrentQps(), result.getLimit()));
                }
            }
        }
        return pjp.proceed();
    }

    private boolean shouldLimit(Object[] args, QpsRateLimit annotation, ProceedingJoinPoint pjp) {
        if(annotation.field().isEmpty()) {
            return true;
        }

        String targetField = annotation.field();
        String[] targetValues = annotation.values();
        boolean hasValueFilter = targetValues.length > 0;
        Set<String> targetValueSet = hasValueFilter ? new HashSet<>(Arrays.asList(targetValues)) : Collections.emptySet();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] parameterNames = signature.getParameterNames();

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if(arg == null) {
                continue;
            }

            if(parameterNames != null && i < parameterNames.length
                    && targetField.equals(parameterNames[i])) {
                if(!hasValueFilter || matchValue(arg, targetValueSet)) {
                    return true;
                }
            }

            Object fieldValue = getFieldValue(arg, targetField);
            if(fieldValue != null && (!hasValueFilter || matchValue(fieldValue, targetValueSet))) {
                return true;
            }
        }
        return false;
    }

    private Object getFieldValue(Object obj, String fieldName) {
        String cacheKey = obj.getClass().getName() + "#" + fieldName;
        Optional<Field> cached = fieldCache.get(cacheKey);
        if(cached == null) {
            cached = resolveField(obj.getClass(), fieldName);
            if(fieldCache.size() < MAX_FIELD_CACHE_SIZE) {
                fieldCache.putIfAbsent(cacheKey, cached);
            }
        }
        if(!cached.isPresent()) {
            return null;
        }
        try {
            return cached.get().get(obj);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private Optional<Field> resolveField(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return Optional.of(f);
        } catch (NoSuchFieldException e) {
            return Optional.empty();
        }
    }

    /**
     * 检查值是否在目标值集合中
     */
    private boolean matchValue(Object value, Set<String> targetValueSet) {
        if(value == null) {
            return false;
        }
        String strValue = String.valueOf(value);
        return targetValueSet.contains(strValue);
    }

    private void setRetryAfterHeader() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletResponse response = attrs.getResponse();
            if(response != null) {
                response.setHeader("Retry-After", "1");
            }
        } catch (Exception ignored) {
        }
    }
}

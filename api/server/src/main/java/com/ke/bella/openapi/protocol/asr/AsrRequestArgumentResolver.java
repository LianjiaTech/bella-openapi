package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
public class AsrRequestArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AsrRequest.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return new AsrRequest();
        }

        AsrRequest asrRequest = new AsrRequest();
        for (Field field : AsrRequest.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(JsonIgnore.class)) {
                continue;
            }
            // skip synthetic fields (e.g. Lombok-generated)
            if (field.isSynthetic()) {
                continue;
            }

            String headerName = resolveHeaderName(field);
            String headerValue = request.getHeader(headerName);

            if (StringUtils.isBlank(headerValue)) {
                continue;
            }

            Object value = parseValue(headerValue, field.getType(), headerName);
            if (value != null) {
                field.setAccessible(true);
                try {
                    field.set(asrRequest, value);
                } catch (IllegalAccessException e) {
                    log.warn("Failed to set field '{}' from header '{}': {}", field.getName(), headerName, e.getMessage());
                }
            }
        }

        // Post-processing: URL decode hotWords
        if (StringUtils.isNotBlank(asrRequest.getHotWords())) {
            try {
                asrRequest.setHotWords(URLDecoder.decode(asrRequest.getHotWords(), StandardCharsets.UTF_8.name()));
            } catch (Exception e) {
                // keep original value
            }
        }

        // Fallback: hotWordsTableId from boosting_table_id header
        if (StringUtils.isBlank(asrRequest.getHotWordsTableId())) {
            String fallback = request.getHeader("boosting_table_id");
            if (StringUtils.isNotBlank(fallback)) {
                asrRequest.setHotWordsTableId(fallback);
            }
        }

        // Apply defaults
        if (StringUtils.isBlank(asrRequest.getFormat())) {
            asrRequest.setFormat("wav");
        }
        if (asrRequest.getSampleRate() == null) {
            asrRequest.setSampleRate(16000);
        }
        if (asrRequest.getMaxSentenceSilence() == null) {
            asrRequest.setMaxSentenceSilence(3000);
        }
        if (asrRequest.getConvertNumbers() == null) {
            asrRequest.setConvertNumbers(false);
        }

        return asrRequest;
    }

    private String resolveHeaderName(Field field) {
        JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        if (jsonProperty != null && StringUtils.isNotBlank(jsonProperty.value())) {
            return jsonProperty.value();
        }
        return camelToSnake(field.getName());
    }

    private String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object parseValue(String value, Class<?> type, String headerName) {
        try {
            if (type == String.class) {
                return value;
            } else if (type == Integer.class || type == int.class) {
                return Integer.parseInt(value.trim());
            } else if (type == Boolean.class || type == boolean.class) {
                return Boolean.parseBoolean(value.trim());
            } else if (type == Double.class || type == double.class) {
                return Double.parseDouble(value.trim());
            }
        } catch (Exception e) {
            log.warn("Failed to parse header '{}' value '{}' as {}: {}", headerName, value, type.getSimpleName(), e.getMessage());
            return null;
        }
        return null;
    }
}

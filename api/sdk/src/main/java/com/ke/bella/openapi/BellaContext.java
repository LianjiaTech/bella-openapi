package com.ke.bella.openapi;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BellaContext {
    public static final String BELLA_TRACE_HEADER = "X-BELLA-TRACE-ID";
    public static final String BELLA_REQUEST_ID_HEADER = "X-BELLA-REQUEST-ID";
    public static final String BELLA_REQUEST_MOCK_HEADER = "X-BELLA-MOCK-REQUEST";
    public static final String BELLA_BILLING_AK_HEADER = "X-BELLA-BILLING-AK-CODE";

    private static final ThreadLocal<Operator> operatorLocal = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, String>> headersThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<ApikeyInfo> akThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<ApikeyInfo> billingAkThreadLocal = new ThreadLocal<>();
    public static String generateTraceId(String serviceId) {
        return serviceId + "-" + UUID.randomUUID();
    }

    public static Map<String, String> getHeaders() {
        if(headersThreadLocal.get() == null) {
            headersThreadLocal.set(new HashMap<>());
        }
        return headersThreadLocal.get();
    }

    public static String getHeader(String key) {
        return getHeaders().get(key);
    }

    public static ApikeyInfo getApikey() {
        Assert.notNull(akThreadLocal.get(), "ak is empty");
        return akThreadLocal.get();
    }

    public static String getTraceId() {
        return getHeaders().get(BELLA_TRACE_HEADER);
    }

    public static String getRequestId() {
        return getHeaders().get(BELLA_REQUEST_ID_HEADER);
    }

    public static boolean isMock() {
        return "true".equalsIgnoreCase(getHeaders().get(BELLA_REQUEST_MOCK_HEADER));
    }

    public static ApikeyInfo getApikeyIgnoreNull() {
        return akThreadLocal.get();
    }

    public static String getAkCode() {
        return getApikeyIgnoreNull() == null ? null : getApikey().getCode();
    }

    public static void setApikey(ApikeyInfo ak) {
        akThreadLocal.set(ak);
    }

    public static ApikeyInfo getBillingAk() {
        return billingAkThreadLocal.get();
    }

    public static void setBillingAk(ApikeyInfo billingAkCode) {
        billingAkThreadLocal.set(billingAkCode);
    }

    public static Operator getOperator() {
        Operator userInfo = operatorLocal.get();
        Assert.notNull(userInfo, "userInfo is null");
        return userInfo;
    }

    public static Operator getOperatorIgnoreNull() {
        return operatorLocal.get();
    }

    public static void setOperator(Operator operator) {
        operatorLocal.set(getPureOper(operator));
    }

    private static Operator getPureOper(Operator oper) {
        // 使用Spring BeanUtils进行属性拷贝，自动复制所有字段，降低维护成本
        Operator copy = new Operator();
        BeanUtils.copyProperties(oper, copy);
        
        // 确保optionalInfo不为null并进行浅拷贝
        if (copy.getOptionalInfo() == null) {
            copy.setOptionalInfo(new HashMap<>());
        } else {
            copy.setOptionalInfo(new HashMap<>(copy.getOptionalInfo()));
        }
        
        return copy;
    }

    public static String getOwnerCode() {
        Operator op = getOperatorIgnoreNull();
        if(op != null) {
            return op.getUserId() == null || op.getUserId() <= 0? op.getSourceId() : op.getUserId().toString();
        }
        ApikeyInfo apikeyInfo = getApikeyIgnoreNull();
        return apikeyInfo == null ? null : apikeyInfo.getOwnerCode();
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> map = new HashMap<>();
        map.put("oper", operatorLocal.get());
        map.put("ak", akThreadLocal.get());
        map.put("headers", headersThreadLocal.get());
        return map;
    }

    @SuppressWarnings({"unchecked"})
    public static void replace(Map<String, Object> map) {
        operatorLocal.set((Operator) map.getOrDefault("oper", new Operator()));
        akThreadLocal.set((ApikeyInfo) map.getOrDefault("ak", new ApikeyInfo()));
        headersThreadLocal.set((Map<String, String>) map.getOrDefault("headers", new HashMap<>()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void replace(String json) {
        Map<String, Object> map = JacksonUtils.deserialize(json, Map.class);
        if(map != null) {
            if(map.containsKey("oper")) {
                map.put("oper", JacksonUtils.convertValue((Map) map.get("oper"), Operator.class));
            }
            if(map.containsKey("ak")) {
                map.put("ak", JacksonUtils.convertValue((Map) map.get("ak"), ApikeyInfo.class));
            }
            if(map.containsKey("headers")) {
                map.put("headers", JacksonUtils.convertValue((Map) map.get("headers"), ApikeyInfo.class));
            }
            replace(map);
        }
    }

    public static final Operator SYS = Operator.builder()
            .userId(0L).userName("system")
            .build();

    public static void clearAll() {
        headersThreadLocal.remove();
        akThreadLocal.remove();
        operatorLocal.remove();
        billingAkThreadLocal.remove();
    }

}

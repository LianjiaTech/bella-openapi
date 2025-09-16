package com.ke.bella.openapi.protocol.images.generator;

import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI文生图适配器
 */
@Component("OpenAIImagesGenerator")
public class OpenAIAdaptor implements ImagesGeneratorAdaptor<ImagesProperty> {
    
    @Override
    public String endpoint() {
        return "/v1/images/generations";
    }
    
    @Override
    public String getDescription() {
        return "OpenAI文生图协议";
    }
    
    @Override
    public Class<?> getPropertyClass() {
        return ImagesProperty.class;
    }
    
    @Override
    public ImagesResponse generateImages(ImagesRequest request, String url, ImagesProperty property) {
        request.setModel(property.getDeployName());
        Object requestBody = buildRequestBody(request);
        Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());

        requestBuilder.url(url)
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
                        JacksonUtils.serialize(requestBody)));

        Request httpRequest = requestBuilder.build();
        return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
    }

    /**
     * 构建请求体，extra_body 参数会覆盖 request 中声明的字段
     */
    private Object buildRequestBody(ImagesRequest request) {
        Map<String, Object> extraBody = request.getExtra_body();

        if (extraBody == null || extraBody.isEmpty()) {
            return request;
        }
        Set<String> declaredFields = getDeclaredFieldNames(ImagesRequest.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> requestMap = JacksonUtils.toMap(request);
        for (Map.Entry<String, Object> entry : extraBody.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (declaredFields.contains(key)) {
				if (requestMap != null) {
					requestMap.put(key, value);
				}
			}
        }
        return requestMap;
    }

    /**
     * 获取类中声明的所有字段名（包括父类）
     */
    private Set<String> getDeclaredFieldNames(Class<?> clazz) {
        Set<String> fieldNames = new HashSet<>();

        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && !field.getName().equals("serialVersionUID")) {
                    fieldNames.add(field.getName());
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        return fieldNames;
    }
}

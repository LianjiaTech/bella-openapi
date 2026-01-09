package com.ke.bella.openapi.utils;

import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.*;

/**
 * 图片编辑请求工具类
 * 用于处理multipart/form-data请求中的图片编辑参数
 */
@Slf4j
public class ImagesEditRequestUtils {

    /**
     * 从MultipartHttpServletRequest读取并构建ImagesEditRequest
     */
    public static ImagesEditRequest readFromMultipartRequest(MultipartHttpServletRequest request) {
        try {
            ImagesEditRequest editRequest = new ImagesEditRequest();

            // 设置基本参数
            setBasicParameters(request, editRequest);

            // 处理图片文件
            processImageFiles(request, editRequest);

            // 处理mask文件
            processMaskFile(request, editRequest);

            // 处理其他参数
            processOtherParameters(request, editRequest);

            return editRequest;
        } catch (Exception e) {
            log.error("Failed to convert multipart request to ImagesEditRequest", e);
            throw new HttpMessageNotReadableException("Failed to read ImagesEditRequest from multipart data", e);
        }
    }

    /**
     * 设置基本参数
     */
    private static void setBasicParameters(MultipartHttpServletRequest request, ImagesEditRequest editRequest) {
        editRequest.setModel(request.getParameter("model"));
        editRequest.setPrompt(request.getParameter("prompt"));
        editRequest.setSize(request.getParameter("size"));
        editRequest.setResponse_format(request.getParameter("response_format"));
        editRequest.setUser(request.getParameter("user"));

        // 处理数字参数
        parseIntegerParameter(request.getParameter("n"), editRequest::setN);
    }

    /**
     * 处理图片文件
     */
    private static void processImageFiles(MultipartHttpServletRequest request, ImagesEditRequest editRequest) {
        List<MultipartFile> imageFiles = collectImageFiles(request);

        if(!imageFiles.isEmpty() && !imageFiles.stream().allMatch(MultipartFile::isEmpty)) {
            MultipartFile[] validFiles = imageFiles.stream()
                    .filter(file -> !file.isEmpty())
                    .toArray(MultipartFile[]::new);

            editRequest.setImage(validFiles);
        }

    }

    /**
     * 处理mask文件
     */
    private static void processMaskFile(MultipartHttpServletRequest request, ImagesEditRequest editRequest) {
        MultipartFile maskFile = request.getFile("mask");
        if(maskFile != null && !maskFile.isEmpty()) {
            editRequest.setMask(maskFile);
        }
    }

    /**
     * 处理其他参数
     */
    private static void processOtherParameters(MultipartHttpServletRequest request, ImagesEditRequest editRequest) {
        processImageUrls(request, editRequest);
        processImageBase64(request, editRequest);
        processExtraBodyParameters(request, editRequest);
    }

    /**
     * 收集图片文件
     */
    private static List<MultipartFile> collectImageFiles(MultipartHttpServletRequest request) {
        List<MultipartFile> imageFiles = new ArrayList<>();
        imageFiles.addAll(request.getFiles("image"));
        imageFiles.addAll(request.getFiles("image[]"));
        return imageFiles;
    }

    /**
     * 处理图片URL参数
     */
    private static void processImageUrls(MultipartHttpServletRequest request, ImagesEditRequest editRequest) {
        String[] urls = mergeParameterArrays(
                request.getParameterValues("image_url"),
                request.getParameterValues("image_url[]"));
        if(urls.length > 0) {
            editRequest.setImage_url(urls);
        }
    }

    /**
     * 处理图片Base64参数
     */
    private static void processImageBase64(MultipartHttpServletRequest request, ImagesEditRequest editRequest) {
        String[] base64s = mergeParameterArrays(
                request.getParameterValues("image_b64_json"),
                request.getParameterValues("image_b64_json[]"));
        if(base64s.length > 0) {
            editRequest.setImage_b64_json(base64s);
        }
    }

    /**
     * 处理额外的body参数
     */
    private static void processExtraBodyParameters(MultipartHttpServletRequest request, ImagesEditRequest editRequest) {
        Set<String> knownParams = getKnownParameters();
        Map<String, String[]> parameterMap = request.getParameterMap();

        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String key = entry.getKey();
            if(!knownParams.contains(key)) {
                Object value = convertParameterValues(entry.getValue());
                editRequest.setExtraBodyField(key, value);
            }
        }
    }

    /**
     * 获取已知参数列表
     */
    private static Set<String> getKnownParameters() {
        return new HashSet<>(Arrays.asList(
                "model", "prompt", "size", "response_format", "user", "n",
                "image_url", "image_url[]", "image_b64_json", "image_b64_json[]"));
    }

    /**
     * 合并参数数组
     */
    private static String[] mergeParameterArrays(String[] array1, String[] array2) {
        List<String> result = new ArrayList<>();
        if(array1 != null) {
            result.addAll(Arrays.asList(array1));
        }
        if(array2 != null) {
            result.addAll(Arrays.asList(array2));
        }
        return result.toArray(new String[0]);
    }

    /**
     * 解析整数参数
     */
    private static void parseIntegerParameter(String paramValue, java.util.function.Consumer<Integer> setter) {
        if(paramValue != null && !paramValue.trim().isEmpty()) {
            try {
                setter.accept(Integer.valueOf(paramValue));
            } catch (NumberFormatException e) {
                log.warn("Invalid integer parameter value: {}", paramValue);
            }
        }
    }

    /**
     * 转换参数值
     */
    private static Object convertParameterValues(String[] values) {
        if(values.length == 1) {
            return convertBasicType(values[0]);
        } else {
            return Arrays.stream(values)
                    .map(ImagesEditRequestUtils::convertBasicType)
                    .toArray();
        }
    }

    /**
     * 简单的类型转换
     */
    private static Object convertBasicType(String value) {
        if(value == null) {
            return null;
        }

        String trimmed = value.trim();

        // 布尔类型
        if("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if("false".equalsIgnoreCase(trimmed)) {
            return false;
        }

        // 整数类型
        if(trimmed.matches("-?\\d+")) {
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                try {
                    return Long.parseLong(trimmed);
                } catch (NumberFormatException e2) {
                    return value;
                }
            }
        }

        // 小数类型
        if(trimmed.matches("-?\\d+\\.\\d+")) {
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException e) {
                return value;
            }
        }

        return value;
    }
}

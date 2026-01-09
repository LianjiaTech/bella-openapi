package com.ke.bella.openapi.mock;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

public class ContentGenerator {

    private static String CONTENT;

    static {
        try {
            // 读取 content.txt 文件
            ClassPathResource resource = new ClassPathResource("com/ke/bella/opnepai/mock/content.txt");
            CONTENT = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CONTENT = ""; // 如果读取失败则使用空字符串
        }
    }

    public static String generateContent(int length) {
        if(length <= 0) {
            return "";
        }

        // 如果请求长度超过文本总长度,返回全部内容
        if(length >= CONTENT.length()) {
            return CONTENT;
        }

        // 否则返回指定长度的内容
        return CONTENT.substring(0, length);
    }
}

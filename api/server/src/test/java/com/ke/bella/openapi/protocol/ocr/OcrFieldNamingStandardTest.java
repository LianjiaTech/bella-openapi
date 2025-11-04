package com.ke.bella.openapi.protocol.ocr;

import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.reflections.Reflections;

import com.ke.bella.openapi.protocol.OpenapiResponse;

/**
 * OCR证件字段命名规范测试
 * 验证所有字段名是否在允许的超集中
 */
public class OcrFieldNamingStandardTest {

    /**
     * 自动扫描所有OCR数据类
     */
    private List<Class<?>> findAllOcrDataClasses() {
        List<Class<?>> classes = new ArrayList<>();

        Reflections reflections = new Reflections("com.ke.bella.openapi.protocol.ocr");
        Set<Class<? extends OpenapiResponse>> responseClasses = reflections.getSubTypesOf(OpenapiResponse.class);

        for (Class<?> responseClass : responseClasses) {
            for (Class<?> innerClass : responseClass.getDeclaredClasses()) {
                if(innerClass.getSimpleName().endsWith("Data") &&
                        Modifier.isStatic(innerClass.getModifiers()) &&
                        Modifier.isPublic(innerClass.getModifiers()) &&
                        Serializable.class.isAssignableFrom(innerClass)) {
                    classes.add(innerClass);
                }
            }
        }

        return classes;
    }

    /**
     * 主测试: 验证所有OCR字段命名规范
     */
    @Test
    public void testAllOcrResponseFieldNamingStandard() {
        System.out.println("=== OCR字段命名规范验证 ===\n");

        // 1. 打印允许的字段名超集
        Set<String> allowedFields = OcrFieldNamingStandard.getAllAllowedFieldNames();
        System.out.println("允许的字段名超集(" + allowedFields.size() + "个):");
        for (String field : allowedFields) {
            System.out.println("  - " + field);
        }
        System.out.println();

        // 2. 打印扫描到的类
        List<Class<?>> ocrDataClasses = findAllOcrDataClasses();
        System.out.println("扫描到的OCR数据类(" + ocrDataClasses.size() + "个):");
        for (Class<?> clazz : ocrDataClasses) {
            System.out.println("  - " + clazz.getName());
        }
        System.out.println();

        // 3. 验证每个类的字段是否符合要求
        List<String> violations = new ArrayList<>();

        for (Class<?> clazz : ocrDataClasses) {
            System.out.println("检查: " + clazz.getSimpleName());
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                String fieldName = field.getName();

                // 跳过特殊字段
                if(fieldName.equals("serialVersionUID") ||
                        fieldName.startsWith("$") ||
                        fieldName.equals("INSTANCE")) {
                    continue;
                }

                // 检查字段是否在超集中
                if(!OcrFieldNamingStandard.isAllowedFieldName(fieldName)) {
                    String violation = clazz.getSimpleName() + "." + fieldName;
                    violations.add(violation);
                    System.out.println("  ❌ " + fieldName + " - 不在超集中");
                } else {
                    System.out.println("  ✅ " + fieldName);
                }
            }
            System.out.println();
        }

        // 结果汇总
        System.out.println("=== 验证结果 ===");
        System.out.println("总类数: " + ocrDataClasses.size());
        System.out.println("不符合规范的字段数: " + violations.size());

        if(!violations.isEmpty()) {
            System.out.println("\n不符合规范的字段:");
            for (String violation : violations) {
                System.out.println("  ❌ " + violation);
            }
            System.out.println("\n提示: 请在 OcrFieldNamingStandard 中添加缺失的字段名");
        }

        // 断言
        assertTrue(
                "发现 " + violations.size() + " 个字段不在允许的超集中",
                violations.isEmpty());

        System.out.println("\n✅ 所有字段命名规范验证通过!");
    }
}

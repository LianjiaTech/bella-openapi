package com.ke.bella.openapi;

import com.ke.bella.openapi.common.exception.ChannelException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Slf4j
public class TypeSchema implements Serializable {
    private static final long serialVersionUID = 1L;
    private String code;
    private String name;
    private String valueType;
    private List<String> selections;
    private JsonSchema child;

    @Override
    public boolean equals(Object o) {
        if(o instanceof TypeSchema) {
            return this.code.equals(((TypeSchema) o).code);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.code.hashCode();
    }

    @SuppressWarnings({ "unchecked", "rawType" })
    public static TypeSchema toSchema(Field field, Map<String, String> desc) {
        if(!desc.isEmpty() && !desc.containsKey(field.getName())) {
            return null;
        }
        try {
            TypeSchema schema = new TypeSchema();
            schema.setCode(field.getName());
            schema.setName(desc.isEmpty() ? field.getName() : desc.get(field.getName()));
            Class<?> fieldType = field.getType();
            if(fieldType.isEnum()) {
                // 枚举类型
                Object[] ems = fieldType.getEnumConstants();
                Method name = fieldType.getMethod("name");
                schema.setValueType("enum");
                schema.setSelections(Arrays.stream(ems).map(em -> {
                    try {
                        return name.invoke(em).toString();
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw ChannelException.fromException(e);
                    }
                }).collect(Collectors.toList()));
            } else if(isPrimitiveOrWrapper(fieldType) || fieldType == String.class || fieldType == BigDecimal.class) {
                // 简单类型： 基本类型、String、BigDecimal
                schema.setValueType(fieldType == boolean.class || fieldType == Boolean.class ? "bool"
                        : fieldType == char.class || fieldType == String.class || fieldType == Character.class ? "string" : "number");
            } else if(ComponentList.class.isAssignableFrom(fieldType)) {
                schema.setValueType("array");
                ComponentList<?> componentList = ((Class<? extends ComponentList<?>>) fieldType).newInstance();
                Class<?> componentType = componentList.getComponentType();
                if(IDescription.class.isAssignableFrom(componentType)) {
                    schema.setChild(JsonSchema.toSchema(componentType));
                }
            } else if(fieldType.isArray()) {
                // 数组类型
                schema.setValueType("array");
                if(IDescription.class.isAssignableFrom(fieldType.getComponentType())) {
                    schema.setChild(JsonSchema.toSchema(fieldType.getComponentType()));
                }
            } else if(Map.class.isAssignableFrom(fieldType)) {
                // Map类型
                schema.setValueType("map");
            } else {
                // 其他复杂类型，递归创建JsonSchema
                schema.setValueType("object");
                schema.setChild(JsonSchema.toSchema(fieldType));
            }
            return schema;
        } catch (NoSuchMethodException e) {
            log.warn(e.getMessage(), e);
            throw ChannelException.fromException(e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
                type == Boolean.class ||
                type == Character.class ||
                type == Byte.class ||
                type == Short.class ||
                type == Integer.class ||
                type == Long.class ||
                type == Float.class ||
                type == Double.class;
    }
}

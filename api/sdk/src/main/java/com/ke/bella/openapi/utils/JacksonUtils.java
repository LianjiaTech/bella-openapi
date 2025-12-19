package com.ke.bella.openapi.utils;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Author: Stan Sai Date: 2024/4/11 11:55 description:
 */
public class JacksonUtils {
    private static final Logger LOGGER = LogManager.getLogger(JacksonUtils.class);
    public static final ObjectMapper MAPPER;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    static {
        MAPPER = serializingObjectMapper();
    }

    private JacksonUtils() {
    }

    public static String serialize(Object obj) {
        if (obj == null) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return "";
    }

    public static byte[] toByte(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return new byte[0];
    }

    /**
     * 将对象序列化到 ByteBuffer
     * 使用 Netty 的 ByteBufOutputStream 直接写入 ByteBuffer，避免中间的 byte[] 分配
     * 适合处理大对象（如图片数据、思维链等）
     *
     * @param obj 要序列化的对象
     * @param size 预估的 DirectByteBuffer 分配大小（建议略大于实际序列化大小）
     * @return DirectByteBuffer，如果序列化失败返回空的 DirectByteBuffer
     */
    public static java.nio.ByteBuffer toByteBuffer(Object obj, int size) {
        if (obj == null) {
            return java.nio.ByteBuffer.allocateDirect(0);
        }

        try {
            // 分配精确大小的 DirectByteBuffer
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(size);

            // 使用 Netty 的 ByteBuf 包装 NIO ByteBuffer
            io.netty.buffer.ByteBuf byteBuf = io.netty.buffer.Unpooled.wrappedBuffer(buffer);
            byteBuf.writerIndex(0); // 重置写入位置

            // 使用 Netty 的 ByteBufOutputStream 进行流式序列化
            io.netty.buffer.ByteBufOutputStream outputStream = new io.netty.buffer.ByteBufOutputStream(byteBuf);
            // 显式转换为 OutputStream 以避免 Jackson 的 createGenerator 方法歧义
            com.fasterxml.jackson.core.JsonGenerator generator = MAPPER.getFactory().createGenerator((java.io.OutputStream) outputStream);
            MAPPER.writeValue(generator, obj);
            generator.close();

            // 调整 NIO ByteBuffer 的 position 和 limit
            buffer.position(0);
            buffer.limit(byteBuf.writerIndex());

            return buffer;

        } catch (java.io.IOException e) {
            LOGGER.error("流式序列化失败，回退到 byte[] 方式: " + e.getMessage(), e);
            // 回退到原始方式
            try {
                byte[] bytes = MAPPER.writeValueAsBytes(obj);
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(bytes.length);
                buffer.put(bytes);
                buffer.flip();
                return buffer;
            } catch (JsonProcessingException ex) {
                LOGGER.error("序列化完全失败: " + ex.getMessage(), ex);
                return java.nio.ByteBuffer.allocateDirect(0);
            }
        }
    }

    public static <T> T deserialize(String jsonText, TypeReference<T> type) {
        if (StringUtils.isBlank(jsonText)) {
            return null;
        }
        try {
            return MAPPER.readValue(jsonText, type);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    public static <T> T deserialize(String jsonText, Class<T> beanClass) {
        if (StringUtils.isBlank(jsonText)) {
            return null;
        }
        try {
            return MAPPER.readValue(jsonText, beanClass);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    public static <T> T deserialize(byte[] bytes, Class<T> beanClass) {
        if (bytes.length == 0) {
            return null;
        }
        try {
            return MAPPER.readValue(bytes, beanClass);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    public static <T> T deserialize(byte[] bytes, TypeReference<T> tTypeReference) {
        if (bytes.length == 0) {
            return null;
        }
        try {
            return MAPPER.readValue(bytes, tTypeReference);
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return null;
    }

    public static JsonNode deserialize(String jsonText) {
        if (StringUtils.isBlank(jsonText)) {
            return null;
        }
        try {
            return MAPPER.readTree(jsonText);
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return null;
    }

    public static JsonNode toJsonNode(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.readTree(serialize(obj));
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return null;
    }

    public static ObjectNode createNode() {
        try {
            return MAPPER.createObjectNode();
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return null;
    }

    public static ArrayNode createArrayNode() {
        try {
            return MAPPER.createArrayNode();
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return null;
    }

    public static Map<String, Object> toMap(byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        try {
            map = MAPPER.readValue(bytes, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return map;
    }

    public static Map<String, Object> toMap(String jsonStr) {
        if(jsonStr == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        try {
            map = MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return map;
    }

    public static Map<String, Object> toMap(Object objEntity) {
        if(Objects.isNull(objEntity)) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        try {
            map = MAPPER.readValue(MAPPER.writeValueAsString(objEntity), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return map;
    }

    public static <T> T convertValue(Map<String, Object> source, Class<T> clazz) {
        return MAPPER.convertValue(source, clazz);
    }


    /**
     * jackson2 json序列化 null字段输出为空串
     */
    public static ObjectMapper serializingObjectMapper() {

        // 设置日期格式
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // 序列化日期格式
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer());
        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer());
        javaTimeModule.addSerializer(Date.class, new DateSerializer(false, simpleDateFormat));
        // 反序列化日期格式
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer());
        javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer());
        javaTimeModule.addDeserializer(Date.class, new JsonDeserializer<Date>() {
            @Override
            public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
                String date = jsonParser.getText();
                try {
                    return simpleDateFormat.parse(date);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        objectMapper.registerModule(javaTimeModule)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // 序列化成json时，将所有的Long变成string，以解决js中的精度丢失。
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(simpleModule);

        // 忽略不存在的字段
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 空值不序列化
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return objectMapper;
    }

    /**
     * LocalDateTime序列化
     */
    private static class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.format(DATETIME_FORMATTER));
        }
    }

    /**
     * LocalDateTime反序列化
     */
    private static class LocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return LocalDateTime.parse(p.getValueAsString(), DATETIME_FORMATTER);
        }
    }

    /**
     * LocalDate序列化
     */
    private static class LocalDateSerializer extends JsonSerializer<LocalDate> {

        @Override
        public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

            // 获取value来源的类
            Class<?> aClass = gen.getCurrentValue().getClass();

            // 获取字段名
            String currentName = gen.getOutputContext().getCurrentName();

            try {
                // 获取字段
                Field declaredField = aClass.getDeclaredField(currentName);
                // 校验是否LocalDate属性的字段
                if(Objects.equals(declaredField.getType(), LocalDate.class)) {
                    // 是否被@JsonFormat修饰
                    boolean annotationPresent = declaredField.isAnnotationPresent(JsonFormat.class);

                    if(annotationPresent) {
                        String pattern = declaredField.getAnnotation(JsonFormat.class).pattern();
                        if(StringUtils.isNotEmpty(pattern)) {
                            gen.writeString(value.format(DateTimeFormatter.ofPattern(pattern)));
                        } else {
                            gen.writeString(value.format(DATE_FORMATTER));

                        }
                    } else {
                        gen.writeString(value.format(DATE_FORMATTER));
                    }

                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    /**
     * LocalDate反序列化
     */
    private static class LocalDateDeserializer extends JsonDeserializer<LocalDate> {

        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return LocalDate.parse(p.getValueAsString(), DATE_FORMATTER);
        }
    }

    /**
     * LocalTime序列化
     */
    private static class LocalTimeSerializer extends JsonSerializer<LocalTime> {

        @Override
        public void serialize(LocalTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.format(TIME_FORMATTER));
        }
    }

    /**
     * LocalTime反序列化
     */
    private static class LocalTimeDeserializer extends JsonDeserializer<LocalTime> {

        @Override
        public LocalTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return LocalTime.parse(p.getValueAsString(), TIME_FORMATTER);
        }
    }
}

package com.ke.bella.openapi.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressUtils {

    private CompressUtils() {
    }

    /**
     * gzip 压缩 + base64 编码
     */
    public static String compress(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        try {
            byte[] data = input.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                gzip.write(data);
            }
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress", e);
        }
    }

    /**
     * base64 解码 + gzip 解压
     */
    public static String decompress(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        try {
            byte[] compressed = Base64.getDecoder().decode(input);
            ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(bis)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = gzip.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                }
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Failed to decompress", e);
        }
    }
}

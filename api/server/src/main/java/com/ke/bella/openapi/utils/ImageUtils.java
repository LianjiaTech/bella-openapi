package com.ke.bella.openapi.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageUtils {
    // 提取图片格式的方法
    public static String extractImageFormat(String text) {
        String formatRegex = "data:image/([a-zA-Z]+);base64,";
        Pattern formatPattern = Pattern.compile(formatRegex);
        Matcher formatMatcher = formatPattern.matcher(text);

        if(formatMatcher.find()) {
            return formatMatcher.group(1);
        } else {
            return null;
        }
    }

    // 提取Base64编码图片信息的方法
    public static String extractBase64ImageData(String text) {
        String base64Regex = "base64,([a-zA-Z0-9+/=]+)";
        Pattern base64Pattern = Pattern.compile(base64Regex);
        Matcher base64Matcher = base64Pattern.matcher(text);

        if(base64Matcher.find()) {
            return base64Matcher.group(1);
        } else {
            return null;
        }
    }

    // 判断是否是data image base64的方法
    public static boolean isDateBase64(String text) {
        return text.startsWith("data:image/");
    }

    // 判断是否是URL的方法
    public static boolean isUrl(String text) {
        return text.startsWith("http://") || text.startsWith("https://");
    }

    // 从URL请求图片并返回字节数组的方法
    public static byte[] fetchImageFromUrl(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoInput(true);
        connection.setConnectTimeout(30000);
        connection.setConnectTimeout(60000);
        connection.connect();

        try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(byteArrayOutputStream)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                bufferedOutputStream.write(buffer, 0, bytesRead);
            }
            bufferedOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        }
    }
}

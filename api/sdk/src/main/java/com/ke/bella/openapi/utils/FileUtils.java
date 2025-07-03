package com.ke.bella.openapi.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import okhttp3.MediaType;
import org.apache.commons.lang3.StringUtils;

public class FileUtils {

    private static final Map<String, String> MIME_TO_EXTENSION = new HashMap<>();
    private static final Map<String, String> EXTENSION_TO_MIME = new HashMap<>();
    static {
        // 初始化MIME类型到文件扩展名的映射
        MIME_TO_EXTENSION.put("text/html", "html");
        MIME_TO_EXTENSION.put("text/css", "css");
        MIME_TO_EXTENSION.put("text/javascript", "js");
        MIME_TO_EXTENSION.put("image/jpg", "jpg");
        MIME_TO_EXTENSION.put("image/jpeg", "jpg");
        MIME_TO_EXTENSION.put("image/png", "png");
        MIME_TO_EXTENSION.put("image/gif", "gif");
        MIME_TO_EXTENSION.put("image/webp", "webp");
        MIME_TO_EXTENSION.put("image/svg+xml", "svg");
        MIME_TO_EXTENSION.put("application/pdf", "pdf");
        MIME_TO_EXTENSION.put("application/zip", "zip");
        MIME_TO_EXTENSION.put("audio/mpeg", "mp3");
        MIME_TO_EXTENSION.put("audio/mp3", "mp3");
        MIME_TO_EXTENSION.put("audio/mp4", "m4a");
        MIME_TO_EXTENSION.put("audio/wav", "wav");
        MIME_TO_EXTENSION.put("audio/wave", "wav");
        MIME_TO_EXTENSION.put("audio/webm", "webm");
        MIME_TO_EXTENSION.put("audio/amr", "amr");
        MIME_TO_EXTENSION.put("video/mp4", "mp4");
        MIME_TO_EXTENSION.put("video/quicktime", "mov");
        MIME_TO_EXTENSION.put("video/mpeg", "mpeg");

        // Microsoft Word文档
        MIME_TO_EXTENSION.put("application/msword", "doc");
        MIME_TO_EXTENSION.put("application/json", "json");
        MIME_TO_EXTENSION.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");

        // Microsoft Excel电子表格
        MIME_TO_EXTENSION.put("application/vnd.ms-excel", "xls");
        MIME_TO_EXTENSION.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

        MIME_TO_EXTENSION.put("application/vnd.ms-powerpoint", "ppt");
        MIME_TO_EXTENSION.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");

        MIME_TO_EXTENSION.put("text/plain", "txt");
        MIME_TO_EXTENSION.put("text/markdown", "md");
        MIME_TO_EXTENSION.put("text/csv", "csv");

        MIME_TO_EXTENSION.put("application/xml", "xml");
        MIME_TO_EXTENSION.put("application/epub+zip", "epub");
        MIME_TO_EXTENSION.put("message/rfc822", "eml");
        MIME_TO_EXTENSION.put("application/vnd.ms-outlook", "msg");
        // 可以根据需要添加更多的MIME类型

        EXTENSION_TO_MIME.put("html", "text/html");
        EXTENSION_TO_MIME.put("css", "text/css");
        EXTENSION_TO_MIME.put("js", "text/javascript");
        EXTENSION_TO_MIME.put("jpg", "image/jpg");
        EXTENSION_TO_MIME.put("jpeg", "image/jpeg");
        EXTENSION_TO_MIME.put("png", "image/png");
        EXTENSION_TO_MIME.put("gif", "image/gif");
        EXTENSION_TO_MIME.put("webp", "image/webp");
        EXTENSION_TO_MIME.put("svg", "image/svg+xml");
        EXTENSION_TO_MIME.put("pdf", "application/pdf");
        EXTENSION_TO_MIME.put("zip", "application/zip");
        EXTENSION_TO_MIME.put("mp3", "audio/mpeg");
        EXTENSION_TO_MIME.put("m4a", "audio/mp4");
        EXTENSION_TO_MIME.put("wav", "audio/wav");
        EXTENSION_TO_MIME.put("webm", "audio/webm");
        EXTENSION_TO_MIME.put("amr", "audio/amr");
        EXTENSION_TO_MIME.put("mp4", "video/mp4");
        EXTENSION_TO_MIME.put("mov", "video/quicktime");
        EXTENSION_TO_MIME.put("mpeg", "video/mpeg");

        EXTENSION_TO_MIME.put("doc", "application/msword");
		EXTENSION_TO_MIME.put("json", "application/json");
        EXTENSION_TO_MIME.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        EXTENSION_TO_MIME.put("xls", "application/vnd.ms-excel");
        EXTENSION_TO_MIME.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        EXTENSION_TO_MIME.put("ppt", "application/vnd.ms-powerpoint");
        EXTENSION_TO_MIME.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        EXTENSION_TO_MIME.put("txt", "text/plain");
        EXTENSION_TO_MIME.put("md", "text/markdown");
        EXTENSION_TO_MIME.put("csv", "text/csv");
        EXTENSION_TO_MIME.put("xml", "application/xml");
        EXTENSION_TO_MIME.put("epub", "application/epub+zip");
        EXTENSION_TO_MIME.put("eml", "message/rfc822");
        EXTENSION_TO_MIME.put("msg", "application/vnd.ms-outlook");
    }

    public static MediaType getMediaType(String contentType) {
        return MediaType.parse(contentType);
    }

    public static String getExtensionFromMimeType(MediaType mediaType) {
        String pure = extraPureMediaType(mediaType);
        return MIME_TO_EXTENSION.getOrDefault(pure.toLowerCase(), "bin");
    }

    /**
     * such as "text", "image", "audio", "video", or "application".
     *
     * @return
     */
    public static String getType(String mediaType) {
        MediaType MediaTypeFiltered = MediaType.parse(mediaType);
        return getType(MediaTypeFiltered);
    }

    public static String getType(MediaType mediaType) {
        String t = extraPureMediaType(mediaType).toLowerCase();
        if(t.startsWith("image")) {
            return "image";
        } else if(t.startsWith("audio")) {
            return "audio";
        } else if(t.startsWith("video")) {
            return "video";
        } else if(t.startsWith("text")) {
            return "text";
        } else {
            return MIME_TO_EXTENSION.getOrDefault(t, "binary");
        }
    }

    public static String getSubType(MediaType mediaType) {
        if(mediaType == null) {
            return null;
        }
        return mediaType.subtype();
    }

    public static String extraPureMediaType(MediaType mediaType) {
        return mediaType.type() + "/" + mediaType.subtype();
    }

    public static MediaType extraMediaType(String filename) {
        String extension = getFileExtension(filename);
        String mimeType = EXTENSION_TO_MIME.get(extension);
        if(StringUtils.isEmpty(mimeType)) {
            return null;
        }
        return MediaType.parse(mimeType);
    }

    public static String getFileExtension(String filename) {
        if(filename == null || filename.lastIndexOf(".") == -1) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    public static byte[] readAllBytes(InputStream inputStream) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            int nRead;
            byte[] data = new byte[2048];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Error reading from InputStream", e);
        }
    }

}

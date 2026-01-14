package com.ke.bella.openapi.common;

import java.util.Arrays;

public enum AudioFormat {
    PCM("pcm", "audio/pcm"),
    MP3("mp3", "audio/mpeg"),
    WAV("wav", "audio/wav"),
    AAC("aac", "audio/aac"),
    OGG("ogg", "audio/ogg"),
    OPUS("opus", "audio/opus"),
    FLAC("flac", "audio/flac"),
    M4A("m4a", "audio/mp4"),
    UNKNOWN("unknown", "application/octet-stream");

    private final String format;
    private final String contentType;

    AudioFormat(String format, String contentType) {
        this.format = format;
        this.contentType = contentType;
    }

    public static String getContentType(String format) {
        if(format == null) {
            return UNKNOWN.contentType;
        }
        return Arrays.stream(values())
                .filter(af -> af.format.equalsIgnoreCase(format))
                .findFirst()
                .map(af -> af.contentType)
                .orElse(UNKNOWN.contentType);
    }
}

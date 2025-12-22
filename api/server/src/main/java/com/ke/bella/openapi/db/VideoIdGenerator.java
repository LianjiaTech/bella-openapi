package com.ke.bella.openapi.db;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class VideoIdGenerator {

    public static final VideoIdGenerator VIDEO_ID_GENERATOR = new VideoIdGenerator();

    private final IDGenerator idGenerator;

    public VideoIdGenerator() {
        this.idGenerator = new IDGenerator("video-");
    }

    public String generate(String spaceCode) {
        return idGenerator.generateWithSpaceCodeHash(spaceCode);
    }

    public static String extractSpaceCodeHash(String videoId) {
        String[] parts = videoId.split("-");
        if(parts.length != 3) {
            throw new IllegalArgumentException("Invalid video ID format: " + videoId);
        }
        return parts[2];
    }

    public static int getShardingIdx(String videoId) {
        String spaceCodeHash = extractSpaceCodeHash(videoId);
        int hash = Math.abs(Integer.parseInt(spaceCodeHash));
        return hash % 16;
    }
}

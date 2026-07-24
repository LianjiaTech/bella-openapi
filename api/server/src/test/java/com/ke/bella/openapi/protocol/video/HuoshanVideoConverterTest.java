package com.ke.bella.openapi.protocol.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class HuoshanVideoConverterTest {

    @Test
    public void convertToHuoshanRequestPassesSeedanceMultimodalParameters() {
        VideoCreateRequest request = new VideoCreateRequest();
        request.setModel("doubao-seedance-2-0-260128");

        Map<String, Object> extraBody = new HashMap<>();
        extraBody.put("content", buildContent());
        extraBody.put("generate_audio", true);
        extraBody.put("ratio", "16:9");
        extraBody.put("duration", 11);
        extraBody.put("watermark", false);
        request.setExtra_body(extraBody);

        HuoshanProperty property = new HuoshanProperty();

        HuoshanVideoRequest huoshanRequest = HuoshanVideoConverter.convertToHuoshanRequest(request, property);

        assertEquals("doubao-seedance-2-0-260128", huoshanRequest.getModel());
        assertTrue(huoshanRequest.getGenerate_audio());
        assertEquals("16:9", huoshanRequest.getRatio());
        assertEquals(Integer.valueOf(11), huoshanRequest.getDuration());
        assertFalse(huoshanRequest.getWatermark());
        assertEquals(5, huoshanRequest.getContent().size());

        HuoshanVideoRequest.Content video = huoshanRequest.getContent().get(3);
        assertEquals("video_url", video.getType());
        assertEquals("reference_video", video.getRole());
        assertNotNull(video.getVideo_url());
        assertEquals("https://example.com/reference.mp4", video.getVideo_url().getUrl());

        HuoshanVideoRequest.Content audio = huoshanRequest.getContent().get(4);
        assertEquals("audio_url", audio.getType());
        assertEquals("reference_audio", audio.getRole());
        assertNotNull(audio.getAudio_url());
        assertEquals("https://example.com/reference.mp3", audio.getAudio_url().getUrl());
    }

    private List<Map<String, Object>> buildContent() {
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", "first-person fruit tea ad");
        content.add(text);

        Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", "https://example.com/first.jpg");
        Map<String, Object> image = new HashMap<>();
        image.put("type", "image_url");
        image.put("image_url", imageUrl);
        image.put("role", "reference_image");
        content.add(image);

        Map<String, Object> tailImageUrl = new HashMap<>();
        tailImageUrl.put("url", "https://example.com/last.jpg");
        Map<String, Object> tailImage = new HashMap<>();
        tailImage.put("type", "image_url");
        tailImage.put("image_url", tailImageUrl);
        tailImage.put("role", "reference_image");
        content.add(tailImage);

        Map<String, Object> videoUrl = new HashMap<>();
        videoUrl.put("url", "https://example.com/reference.mp4");
        Map<String, Object> video = new HashMap<>();
        video.put("type", "video_url");
        video.put("video_url", videoUrl);
        video.put("role", "reference_video");
        content.add(video);

        Map<String, Object> audioUrl = new HashMap<>();
        audioUrl.put("url", "https://example.com/reference.mp3");
        Map<String, Object> audio = new HashMap<>();
        audio.put("type", "audio_url");
        audio.put("audio_url", audioUrl);
        audio.put("role", "reference_audio");
        content.add(audio);

        return content;
    }
}

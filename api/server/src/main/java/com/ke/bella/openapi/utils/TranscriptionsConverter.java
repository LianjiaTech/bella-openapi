package com.ke.bella.openapi.utils;

import com.ke.bella.openapi.protocol.asr.flash.FlashAsrResponse;
import com.ke.bella.openapi.protocol.asr.transcription.TranscriptionsResponse;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Flash ASR 响应转换为 OpenAI 转录格式的工具类
 */
public class TranscriptionsConverter {

    /**
     * 将 Flash ASR 响应转换为 OpenAI 转录响应格式
     *
     * @param flashResponse  Flash ASR 响应
     * @param responseFormat 响应格式 (json, verbose_json, text, srt, vtt)
     * 
     * @return OpenAI 格式的转录响应
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static TranscriptionsResponse convertFlashAsrToOpenAI(FlashAsrResponse flashResponse, String responseFormat) {
        if(flashResponse.getFlashResult() == null || flashResponse.getFlashResult().getSentences() == null) {
            return TranscriptionsResponse.builder().text("").build();
        }

        // 合并所有句子为完整文本
        String fullText = flashResponse.getFlashResult().getSentences().stream()
                .map(FlashAsrResponse.Sentence::getText)
                .collect(Collectors.joining(" "));

        TranscriptionsResponse.TranscriptionsResponseBuilder responseBuilder = TranscriptionsResponse.builder()
                .text(fullText)
                .duration((double) flashResponse.getFlashResult().getDuration() / 1000.0); // 毫秒转秒

        // 对于 verbose_json 格式，添加详细的段落信息
        if("verbose_json".equals(responseFormat)) {
            List<TranscriptionsResponse.Segment> segments = IntStream.range(0, flashResponse.getFlashResult().getSentences().size())
                    .mapToObj(i -> {
                        FlashAsrResponse.Sentence sentence = flashResponse.getFlashResult().getSentences().get(i);
                        return TranscriptionsResponse.Segment.builder()
                                .id(i)
                                .seek(0) // Flash ASR 不提供 seek 信息
                                .start((double) sentence.getBeginTime() / 1000.0) // 毫秒转秒
                                .end((double) sentence.getEndTime() / 1000.0) // 毫秒转秒
                                .text(sentence.getText())
                                .build();
                    })
                    .collect(Collectors.toList());
            responseBuilder.segments(segments);
        }

        return responseBuilder.build();
    }

    /**
     * 从文件名获取音频格式
     *
     * @param filename 文件名
     * 
     * @return 音频格式（小写），默认为 "wav"
     */
    public static String getAudioFormatFromFilename(String filename) {
        if(filename == null || !filename.contains(".")) {
            return "wav";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}

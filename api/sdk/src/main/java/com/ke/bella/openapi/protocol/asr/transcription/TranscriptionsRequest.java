package com.ke.bella.openapi.protocol.asr.transcription;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.ISummary;
import com.theokanning.openai.assistants.IUssrRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * OpenAI-compatible transcriptions request for multipart form data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionsRequest implements IUssrRequest, ISummary {

    /**
     * The audio file to transcribe
     */
    @JsonIgnore
    private MultipartFile file;

    /**
     * ID of the model to use
     */
    private String model;

    /**
     * The language of the input audio
     */
    private String language;

    /**
     * An optional text to guide the model's style
     */
    private String prompt;

    /**
     * The format of the transcript output
     */
    @JsonProperty("response_format")
    private String responseFormat = "json";

    /**
     * The sampling temperature, between 0 and 1
     */
    private Double temperature;

    private String user;

    @Override
    public String[] ignoreFields() {
        return new String[] {"file"};
    }
}

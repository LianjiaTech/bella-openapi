package com.ke.bella.openapi.protocol.asr;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpeechContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type;
    private List<Item> items;

    @JsonIgnore
    private Map<String, Object> extraFields = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtraFieldsForJson() {
        return extraFields.isEmpty() ? null : extraFields;
    }

    @JsonAnySetter
    public void setExtraField(String name, Object value) {
        extraFields.put(name, value);
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        private String text;

        @JsonProperty("image_url")
        private String imageUrl;

        @JsonIgnore
        private Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getExtraFieldsForJson() {
            return extraFields.isEmpty() ? null : extraFields;
        }

        @JsonAnySetter
        public void setExtraField(String name, Object value) {
            extraFields.put(name, value);
        }

        public Map<String, Object> toContextData() {
            Map<String, Object> contextData = new LinkedHashMap<>(extraFields);
            if(text != null) {
                contextData.put("text", text);
            }
            if(imageUrl != null) {
                contextData.put("image_url", imageUrl);
            }
            return contextData;
        }
    }
}

package com.ke.bella.openapi.protocol.web;

import com.fasterxml.jackson.annotation.*;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class DoubaoSearchRequest implements UserRequest, Serializable, IMemoryClearable {
    @JsonIgnore private String model;
    @JsonProperty("Query") private String query;
    @JsonProperty("SearchType") private String searchType = "web";
    @JsonProperty("Count") private Integer count;
    @JsonProperty("Filter") private Map<String, Object> filter;
    @JsonProperty("QueryControl") private Map<String, Object> queryControl;
    @JsonProperty("TimeRange") private String timeRange;
    @JsonProperty("ContentFormats") private String contentFormats;
    @JsonProperty("Industry") private String industry;
    @JsonProperty("EnableWaiting") private Boolean enableWaiting;
    @JsonProperty("MaxWaitTime") private Integer maxWaitTime;
    @JsonIgnore private String user;
    @JsonIgnore private boolean cleared;
    @JsonIgnore private Map<String, Object> extraFields = new LinkedHashMap<>();
    @JsonAnySetter public void setExtraField(String key, Object value) { if(!"model".equals(key) && !"user".equals(key)) extraFields.put(key, value); }
    @JsonAnyGetter public Map<String, Object> getExtraFields() { return extraFields; }
    public String validate() {
        if(query == null || query.length() < 1 || query.length() > 100) return "Query must contain 1 to 100 characters";
        if(!"web".equals(searchType) && !"image".equals(searchType)) return "SearchType must be web or image";
        int maxCount = "image".equals(searchType) ? 5 : 50;
        if(count != null && (count < 1 || count > maxCount)) return "Count must be between 1 and " + maxCount;
        if(maxWaitTime != null && (maxWaitTime < 0 || maxWaitTime > 10000)) return "MaxWaitTime must be at most 10000";
        return null;
    }
    @Override public void clearLargeData() { filter = null; queryControl = null; cleared = true; }
    @Override public boolean isCleared() { return cleared; }
}

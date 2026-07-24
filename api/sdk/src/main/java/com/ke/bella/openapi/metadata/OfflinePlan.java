package com.ke.bella.openapi.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflinePlan {
    private String root;
    private List<String> affectedModels;
    private List<Edge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        private String from;
        private String to;
    }
}

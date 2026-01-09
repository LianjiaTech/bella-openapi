package com.ke.bella.openapi.apikey;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.utils.MatchUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApikeyInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private String apikey;
    private String code;
    private String serviceId;
    private String akSha;
    private String akDisplay;
    private String name;
    private String outEntityCode;
    private String parentCode;
    private String ownerType;
    private String ownerCode;
    private String ownerName;
    private String roleCode;
    @JsonIgnore
    private String path;
    private String safetySceneCode;
    private Byte safetyLevel;
    private BigDecimal monthQuota;
    private RolePath rolePath;
    private String status;
    private String remark;
    private Long userId;
    private ApikeyInfo parentInfo;

    public RolePath getRolePath() {
        if(rolePath != null) {
            return rolePath;
        }
        if(path == null) {
            return new RolePath();
        }
        rolePath = JacksonUtils.deserialize(path, RolePath.class);
        return rolePath;
    }

    public boolean hasPermission(String url) {
        return getRolePath().getIncluded().stream().anyMatch(pattern -> MatchUtils.matchUrl(pattern, url))
                && getRolePath().getExcluded().stream().noneMatch(pattern -> MatchUtils.matchUrl(pattern, url));
    }

    public boolean hasAllocatedPermission() {
        return EntityConstants.HIGH.equals(roleCode) ||
                EntityConstants.CONSOLE.equals(roleCode) || EntityConstants.ALL.equals(roleCode);
    }

    @Data
    public static class RolePath implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<String> included = new ArrayList<>();
        private List<String> excluded = new ArrayList<>();
    }
}

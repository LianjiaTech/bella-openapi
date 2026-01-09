package com.ke.bella.openapi.login.cas;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CasProperties {
    private String serverUrlPrefix;
    private String serverLoginUrl;
    private String clientHost;
    private String clientUri = "/console/cas";
    private String clientIndexUrl;
    private boolean clientSupport;
    private String source = "cas";
    private boolean useCasUserId = true;
    private String idAttribute = "ucid";
    private String nameAttribute = "displayName";
    private String emailAttribute = "email";
    private List<String> optionalAttributes = Lists.newArrayList();
}

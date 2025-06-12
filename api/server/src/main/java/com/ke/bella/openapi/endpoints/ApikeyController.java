package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.apikey.ApikeyCreateOp;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.SubApikeyUpdateOp;
import com.ke.bella.openapi.service.ApikeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@BellaAPI
@RestController
@RequestMapping("/v1/apikey")
@Tag(name = "信息查询")
public class ApikeyController {

    @Autowired
    private ApikeyService as;

    @PostMapping("/create")
    public String createSubApikey(@RequestBody ApikeyCreateOp op) {
        Assert.notNull(op.getMonthQuota(), "配额应不可为null");
        Assert.notNull(op.getSafetyLevel(), "安全等级不可为空");
        Assert.isTrue(StringUtils.isNotEmpty(op.getRoleCode()) || CollectionUtils.isNotEmpty(op.getPaths()), "权限不可为空");
        // 如果请求中没有parentCode，则使用当前AK作为父AK
        if (StringUtils.isEmpty(op.getParentCode())) {
            ApikeyInfo cur = EndpointContext.getApikey();
            op.setParentCode(cur.getCode());
        }
        return as.createByParentCode(op);
    }

    @PostMapping("/update")
    public boolean updateSubApikey(@RequestBody SubApikeyUpdateOp op) {
        Assert.hasText(op.getCode(), "ak code不可为空");
        return as.updateSubApikey(op);
    }

    @GetMapping("/whoami")
    public ApikeyInfo whoami() {
        return EndpointContext.getApikey();
    }

    @GetMapping("/permission/check")
    public Boolean permissionCheck(@RequestParam String url) {
        return EndpointContext.getApikey().hasPermission(url);
    }
}

package com.ke.bella.openapi.console;

import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.ApikeyOps;
import com.ke.bella.openapi.db.repo.Page;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import com.ke.bella.openapi.tables.pojos.ApikeyMonthCostDB;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import com.ke.bella.openapi.utils.DateTimeUtils;

@BellaAPI
@RestController
@RequestMapping("/console/apikey")
@Tag(name = "API Key管理")
public class ApikeyConsoleController {
    @Autowired
    private ApikeyService apikeyService;
    @PostMapping("/apply")
    public String apply(@RequestBody ApikeyOps.ApplyOp op) {
        Assert.isTrue(op.getMonthQuota() == null || op.getMonthQuota().doubleValue() > 0, "配额应大于0");
        return apikeyService.apply(op);
    }

    @PostMapping("/reset")
    public String reset(@RequestBody ApikeyOps.CodeOp op) {
        Assert.hasText(op.getCode(), "code不可为空");
        return apikeyService.reset(op);
    }

    @PostMapping("/rename")
    public Boolean rename(@RequestBody ApikeyOps.NameOp op) {
        Assert.hasText(op.getCode(), "code不可为空");
        Assert.notNull(op.getName(), "name不可为null");
        apikeyService.rename(op);
        return true;
    }

    @PostMapping("/bindService")
    public Boolean bindService(@RequestBody ApikeyOps.ServiceOp op) {
        Assert.hasText(op.getCode(), "code不可为空");
        Assert.notNull(op.getServiceId(), "name不可为null");
        apikeyService.bindService(op);
        return true;
    }

    @PostMapping("/role/update")
    public Boolean updateRole(@RequestBody ApikeyOps.RoleOp op) {
        Assert.hasText(op.getCode(), "code不可为空");
        Assert.isTrue(StringUtils.isNotEmpty(op.getRoleCode())
                        || CollectionUtils.isNotEmpty(op.getPaths()), "权限不可为空");
        apikeyService.updateRole(op);
        return true;
    }

    @PostMapping("/quota/update")
    public Boolean updateQuota(@RequestBody ApikeyOps.QuotaOp op) {
        Assert.hasText(op.getCode(), "code不可为空");
        Assert.notNull(op.getMonthQuota(), "配额不可为空");
        Assert.isTrue(op.getMonthQuota().doubleValue() > 0, "配额应大于0");
        apikeyService.updateQuota(op);
        return true;
    }

    @PostMapping("/certify")
    public Boolean certify(@RequestBody ApikeyOps.CertifyOp op) {
        Assert.hasText(op.getCode(), "code不可为空");
        Assert.notNull(op.getCertifyCode(), "认证码不可为空");
        apikeyService.certify(op);
        return true;
    }

    @PostMapping("/activate")
    public Boolean activate(@RequestBody ApikeyOps.CodeOp op) {
        Assert.hasText(op.getCode(), "code不可为空");
        apikeyService.changeStatus(op, true);
        return true;
    }

    @PostMapping("/inactivate")
    public Boolean inactivate(@RequestBody ApikeyOps.CodeOp op) {
        Assert.hasText(op.getCode(), "code不可为空");
        apikeyService.changeStatus(op, false);
        return true;
    }


    @GetMapping("/cost/{akCode}")
    public List<ApikeyMonthCostDB> listApiKeyBillings(@PathVariable String akCode) {
        return apikeyService.queryBillingsByAkCode(akCode);
    }

    @GetMapping("/balance/{akCode}")
    public Map<String, Object> getApiKeyBalance(@PathVariable String akCode) {
        String currentMonth = DateTimeUtils.getCurrentMonth();
        BigDecimal monthCost = apikeyService.loadCost(akCode, currentMonth);
        ApikeyInfo apiKeyInfo = apikeyService.queryByCode(akCode, true);
        Map<String, Object> result = new HashMap<>();
        result.put("akCode", akCode);
        result.put("month", currentMonth);
        result.put("cost", monthCost);
        result.put("quota", apiKeyInfo != null ? apiKeyInfo.getMonthQuota() : BigDecimal.ZERO);
        result.put("balance", apiKeyInfo != null ? apiKeyInfo.getMonthQuota().subtract(monthCost) : BigDecimal.ZERO);
        return result;
    }

    @GetMapping("/fetchByCode")
    public ApikeyInfo fetchByCode(@RequestParam("code") String code, @RequestParam(value = "onlyActive", required = false) boolean onlyActive) {
        return apikeyService.queryByCode(code, onlyActive);
    }

    @GetMapping("/fetchBySha")
    public ApikeyInfo fetchBySha(@RequestParam("sha") String sha, @RequestParam(value = "onlyActive", required = false) boolean onlyActive) {
        return apikeyService.queryBySha(sha, onlyActive);
    }

    @GetMapping("/page")
    public Page<ApikeyDB> pageApikey(ApikeyOps.ApikeyCondition condition) {
        return apikeyService.pageApikey(condition);
    }
}

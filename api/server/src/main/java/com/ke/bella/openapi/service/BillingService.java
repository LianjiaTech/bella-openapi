package com.ke.bella.openapi.service;

import com.ke.bella.openapi.billing.BillingOps;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.db.repo.BillingRepo;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.ke.bella.openapi.billing.BillingOps.GRANULARITY_DAY;
import static com.ke.bella.openapi.billing.BillingOps.GRANULARITY_MONTH;

@Component
public class BillingService {
    private static final Pattern PT_PATTERN = Pattern.compile("\\d{14}");
    private static final DateTimeFormatter PT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private ApikeyService apikeyService;

    @Autowired
    private AkPermissionChecker akPermissionChecker;

    @Autowired
    private BillingRepo billingRepo;

    public List<BillingOps.BillingAkOption> listAkOptions() {
        return apikeyService.listBillingQueryableAkOptions();
    }

    public BillingOps.BillingRecordPage pageRecords(BillingOps.BillingRecordCondition condition) {
        validateCondition(condition);
        Set<String> validatedAkCodes = validateAkCodes(condition.getAkCodes());
        return billingRepo.pageRecords(condition, validatedAkCodes);
    }

    public BillingOps.BillingRecordPage pageAdminRecords(BillingOps.BillingRecordCondition condition) {
        assertAdminPermission();
        validateCondition(condition);
        Set<String> requestedAkCodes = condition.getAkCodes();
        return billingRepo.pageRecords(condition, requestedAkCodes);
    }

    private void validateCondition(BillingOps.BillingRecordCondition condition) {
        Assert.notNull(condition, "查询条件不可为空");
        Assert.isTrue(GRANULARITY_DAY.equals(condition.getGranularity()) || GRANULARITY_MONTH.equals(condition.getGranularity()),
                "granularity仅支持day或month");
        Assert.isTrue(CollectionUtils.isNotEmpty(condition.getAkCodes()), "akCodes不可为空");
        Assert.isTrue(condition.getPage() > 0, "page必须大于0");
        Assert.isTrue(condition.getSize() > 0 && condition.getSize() <= MAX_PAGE_SIZE, "size必须在1-100之间");
        LocalDateTime start = parsePt(condition.getStartPt(), "startPt");
        LocalDateTime end = parsePt(condition.getEndPt(), "endPt");
        Assert.isTrue(!start.isAfter(end), "startPt不可晚于endPt");
        if(GRANULARITY_DAY.equals(condition.getGranularity())) {
            LocalDate today = LocalDate.now(BILLING_ZONE);
            LocalDateTime min = today.withDayOfMonth(1).atStartOfDay();
            LocalDateTime max = today.minusDays(1).atTime(LocalTime.MAX.withNano(0));
            Assert.isTrue(!max.isBefore(min), "日账单当前无可查询日期");
            Assert.isTrue(!start.isBefore(min) && !end.isAfter(max), "日账单仅支持查询当前月1日至前一天");
        }
    }

    private LocalDateTime parsePt(String pt, String fieldName) {
        Assert.isTrue(pt != null && PT_PATTERN.matcher(pt).matches(), fieldName + "必须为yyyyMMddHHmmss格式");
        try {
            return LocalDateTime.parse(pt, PT_FORMATTER);
        } catch(DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + "必须为有效时间", e);
        }
    }

    private Set<String> validateAkCodes(Set<String> requestedAkCodes) {
        Set<String> queryableAkCodes = apikeyService.resolveBillingQueryableAkCodes();
        if(!queryableAkCodes.containsAll(requestedAkCodes)) {
            throw new BellaException.AuthorizationException("没有账单查询权限");
        }
        return requestedAkCodes;
    }

    private void assertAdminPermission() {
        if(!akPermissionChecker.hasAdminPermission()) {
            throw new BellaException.AuthorizationException("没有账单查询权限");
        }
    }

}

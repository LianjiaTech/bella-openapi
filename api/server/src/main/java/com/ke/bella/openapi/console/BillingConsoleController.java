package com.ke.bella.openapi.console;

import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.billing.BillingOps;
import com.ke.bella.openapi.billing.IBillingCostService;
import com.ke.bella.openapi.service.BillingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@BellaAPI
@RestController
@RequestMapping("/console/billing")
@Tag(name = "账单查询")
public class BillingConsoleController {
    @Autowired
    private BillingService billingService;

    @Autowired
    private ObjectProvider<IBillingCostService> billingCostService;

    @PostMapping("/cost")
    public BillingOps.BillingCostResponse getBillingCost(@RequestBody BillingOps.BillingCostRequest request) {
        return billingCostService.getIfAvailable(() -> IBillingCostService.DEFAULT).getBillingCost(request);
    }

    @GetMapping("/ak-options")
    public List<BillingOps.BillingAkOption> listAkOptions() {
        return billingService.listAkOptions();
    }

    @PostMapping("/records")
    public BillingOps.BillingRecordPage pageRecords(@RequestBody BillingOps.BillingRecordCondition condition) {
        return billingService.pageRecords(condition);
    }

    @PostMapping("/admin/records")
    public BillingOps.BillingRecordPage pageAdminRecords(@RequestBody BillingOps.BillingRecordCondition condition) {
        return billingService.pageAdminRecords(condition);
    }
}

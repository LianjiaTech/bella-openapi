package com.ke.bella.openapi.billing;

import com.ke.bella.openapi.common.exception.BellaException;

public interface IBillingCostService {
    IBillingCostService DEFAULT = new IBillingCostService() {
    };

    default BillingOps.BillingCostResponse getBillingCost(BillingOps.BillingCostRequest request) {
        throw BellaException.fromResponse(501, "billing cost service not implemented");
    }
}

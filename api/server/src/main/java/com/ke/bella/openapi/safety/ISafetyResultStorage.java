package com.ke.bella.openapi.safety;

public interface ISafetyResultStorage {

    void addRiskData(Object riskData, boolean isRequest);

    Object getRequestRiskData();

    Object getResponseRiskData();
}

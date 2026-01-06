package com.ke.bella.openapi.safety;

public interface ISafetyResultStorageDelegator extends ISafetyResultStorage {

    @Override
    default void addRiskData(Object riskData, boolean isRequest) {
        if(getStorage() == null) {
            return;
        }
        getStorage().addRiskData(riskData, isRequest);
    }

    @Override
    default Object getRequestRiskData() {
        if(getStorage() == null) {
            return null;
        }
        return getStorage().getRequestRiskData();
    }

    @Override
    default Object getResponseRiskData() {
        if(getStorage() == null) {
            return null;
        }
        return getStorage().getResponseRiskData();
    }

    ISafetyResultStorage getStorage();
}

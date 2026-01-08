package com.ke.bella.openapi.safety;

public class SafetyCheckHelper {

    public static <T extends SafetyCheckRequest> SafetyCheckDelegator<T>  createDelegator(
            ISafetyCheckService<T> safetyService,
            String safetyCheckMode) {

        // Create separate Storage instance
        ISafetyResultStorage storage = new LinkedQueueSafetyResultStorage();

        // Inject Storage into Delegator
        return new SafetyCheckDelegator<>(safetyService, SafetyCheckMode.fromString(safetyCheckMode), storage);
    }

    public static Object getRequestRiskData(ISafetyCheckService<?> safetyService) {
        if(safetyService instanceof ISafetyResultStorage) {
            return ((ISafetyResultStorage)safetyService).getRequestRiskData();
        }
        return null;
    }

    public static Object getResponseRiskData(ISafetyCheckService<?> safetyService) {
        if(safetyService instanceof ISafetyResultStorage) {
            return ((ISafetyResultStorage)safetyService).getResponseRiskData();
        }
        return null;
    }
}

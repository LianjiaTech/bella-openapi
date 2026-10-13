package com.ke.bella.openapi.safety;

public interface ISafetyAuditService {
    Byte fetchLevelByCertifyCode(String certifyCode);
}

package com.ke.bella.openapi.protocol.asr;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TencentV2Property extends TencentProperty {
    public TencentV2Property() {
        setEngineModelType(null);
    }
}

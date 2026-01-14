package com.ke.bella.openapi.protocol.video;

import com.ke.bella.openapi.protocol.IProtocolProperty;

public interface VideoProperty extends IProtocolProperty {

    default Integer getRpm() {
        return null;
    }
}

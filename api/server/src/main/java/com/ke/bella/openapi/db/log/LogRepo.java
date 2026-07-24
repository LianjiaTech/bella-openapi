package com.ke.bella.openapi.db.log;

import com.ke.bella.openapi.EndpointProcessData;

public interface LogRepo {
    void record(EndpointProcessData log);
}

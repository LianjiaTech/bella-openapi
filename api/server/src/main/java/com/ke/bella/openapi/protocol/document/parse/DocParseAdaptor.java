package com.ke.bella.openapi.protocol.document.parse;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;

public interface DocParseAdaptor<T extends DocParseProperty> extends IProtocolAdaptor {

    DocParseTaskInfo parse(DocParseRequest request, String channelCode, String url, T property);

    DocParseResponse queryResult(String taskId, String url, T property);

    default String endpoint() {
        return "/v1/document/parse";
    }
}

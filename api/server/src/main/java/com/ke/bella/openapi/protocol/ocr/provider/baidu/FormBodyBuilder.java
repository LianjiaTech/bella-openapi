package com.ke.bella.openapi.protocol.ocr.provider.baidu;

import okhttp3.FormBody;

@FunctionalInterface
public interface FormBodyBuilder {
    void addFields(FormBody.Builder builder);
}

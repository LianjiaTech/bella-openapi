package com.ke.bella.openapi.safety;

import java.util.function.Supplier;

public interface ISafetyCheckService<T extends SafetyCheckRequest> {
    Object safetyCheck(T request, boolean isMock);

    /**
     * Prepare an async safety call before it is submitted to the worker pool.
     * Implementations whose business payload may be cleared or mutated must
     * materialize everything needed by the returned supplier here. The default
     * keeps the legacy behavior for payloads that remain valid asynchronously.
     */
    default Supplier<Object> prepareAsyncCheck(T request, boolean isMock) {
        return () -> safetyCheck(request, isMock);
    }

    interface IChatSafetyCheckService extends ISafetyCheckService<SafetyCheckRequest.Chat> {
    }

    interface IImageSafetyCheckService extends ISafetyCheckService<SafetyCheckRequest.Image> {
        /**
         * Image payloads may contain multipart resources and mutable response
         * objects. Image integrations must therefore prepare an independent
         * async call before the adaptor or log pipeline can clear them.
         */
        @Override
        Supplier<Object> prepareAsyncCheck(SafetyCheckRequest.Image request, boolean isMock);
    }
}

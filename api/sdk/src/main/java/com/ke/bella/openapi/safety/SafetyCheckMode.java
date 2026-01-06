package com.ke.bella.openapi.safety;

/**
 * Safety check modes for channel-level configuration
 */
public enum SafetyCheckMode {
    /**
     * Skip safety check - no request sent to safety service
     */
    skip,

    /**
     * Async safety check - send request but don't wait for result, don't block main flow
     * This is the default mode for backward compatibility
     */
    async,

    /**
     * Sync safety check - wait for result and potentially block the request
     * This is the original behavior before async/skip modes were introduced
     */
    sync;

    /**
     * Get the default safety check mode
     */
    public static SafetyCheckMode getDefault() {
        return async;
    }

    /**
     * Parse mode from string, returns default (async) if null or invalid
     */
    public static SafetyCheckMode fromString(String mode) {
        if (mode == null) {
            return getDefault();
        }
        try {
            return valueOf(mode.toLowerCase());
        } catch (IllegalArgumentException e) {
            return getDefault();
        }
    }
}
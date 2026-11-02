package edu.campus.lostfound.common;

import org.slf4j.MDC;

/**
 * 统一响应结构。code 为 0 表示成功，非 0 为业务或系统错误码。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, currentTraceId());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, currentTraceId());
    }

    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.TRACE_ID);
    }
}


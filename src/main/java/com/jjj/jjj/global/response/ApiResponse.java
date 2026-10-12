package com.jjj.jjj.global.response;

public record ApiResponse<T>(
        boolean success,
        T data,
        T error
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(T error) {
        return new ApiResponse<>(false, null, error);
    }
}

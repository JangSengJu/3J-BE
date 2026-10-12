package com.jjj.jjj.global.exception.dto;

import com.jjj.jjj.global.exception.ErrorCode;
import lombok.Builder;

import java.time.Instant;

@Builder
public record ErrorDTO(
        int status,
        String code,
        String message,
        Instant timestamp
) {
    public static ErrorDTO from(ErrorCode errorCode) {
        return new ErrorDTO(
            errorCode.getStatusCode(),
            errorCode.getErrorCode(),
            errorCode.getErrorMessage(),
            Instant.now()
        );
    }

}
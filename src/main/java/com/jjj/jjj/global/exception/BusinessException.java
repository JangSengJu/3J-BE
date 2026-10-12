package com.jjj.jjj.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getErrorMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String description) {
        super(description);
        this.errorCode = errorCode;
    }
}
package com.jjj.jjj.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    NOT_VALID_DTO_ERR(400, "NOT_VALID_DTO_ERR", "유효하지 않은 요청입니다."),

    UNAUTHORIZED(401, "UNAUTHORIZED", "로그인이 필요합니다."),

    INVALID_CREDENTIALS(401, "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다."),

    REFRESH_TOKEN_INVALID(401, "REFRESH_TOKEN_INVALID", "로그인이 필요합니다."),

    FORBIDDEN(403, "FORBIDDEN", "권한이 없습니다."),

    NOT_FOUND(404, "NOT_FOUND", "리소스가 없습니다."),

    DUPLICATED_USERNAME(409, "DUPLICATED_USERNAME", "이미 가입된 아이디입니다."),

    SERVICE_UNAVAILABLE(503, "SERVICE_UNAVAILABLE", "AI 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    INTERNAL_SERVER_ERR(500, "INTERNAL_SERVER_ERR", "서버 측 오류가 발생했습니다.");

    private Integer statusCode;
    private String errorCode;
    private String errorMessage;
}

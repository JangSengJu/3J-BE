package com.jjj.jjj.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SignInRequest(
        @NotBlank(message = "아이디를 입력하세요")
        String id,

        @NotBlank(message = "비밀번호를 입력하세요")
        String password
) {
}

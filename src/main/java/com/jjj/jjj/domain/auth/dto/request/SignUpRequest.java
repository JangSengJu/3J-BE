package com.jjj.jjj.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank(message = "아이디를 입력하세요")
        @Size(min = 4, max = 120, message = "아이디는 4자 이상 120자 이하여야 합니다")
        String id,

        @NotBlank(message = "비밀번호를 입력하세요")
        @Size(min = 8, max = 200, message = "비밀번호는 8자 이상 200자 이하여야 합니다")
        String password
) {
    public SignUpRequest {
        if (id != null) {
            id = id.trim().toLowerCase();
        }
    }
}

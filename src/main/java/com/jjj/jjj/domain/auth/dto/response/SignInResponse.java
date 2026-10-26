package com.jjj.jjj.domain.auth.dto.response;

public record SignInResponse(
        String accessToken,
        UserResponse user
) {
}

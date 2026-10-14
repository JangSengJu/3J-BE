package com.jjj.jjj.domain.auth.controller;

import com.jjj.jjj.domain.auth.dto.request.SignInRequest;
import com.jjj.jjj.domain.auth.dto.request.SignUpRequest;
import com.jjj.jjj.domain.auth.dto.response.AccessTokenResponse;
import com.jjj.jjj.domain.auth.dto.response.SignInResponse;
import com.jjj.jjj.domain.auth.dto.response.SignUpResponse;
import com.jjj.jjj.domain.auth.service.AuthService;
import com.jjj.jjj.domain.auth.util.RefreshTokenCookieProvider;
import com.jjj.jjj.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signUp(request)));
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<SignInResponse>> signIn(@Valid @RequestBody SignInRequest request) {
        AuthService.SignInResult result = authService.signIn(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
            @CookieValue(value = RefreshTokenCookieProvider.REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        AuthService.RefreshResult result = authService.refresh(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(new AccessTokenResponse(result.accessToken())));
    }

    @PostMapping("/signout")
    public ResponseEntity<Void> signOut(
            @CookieValue(value = RefreshTokenCookieProvider.REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        authService.signOut(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.delete().toString())
                .build();
    }
}

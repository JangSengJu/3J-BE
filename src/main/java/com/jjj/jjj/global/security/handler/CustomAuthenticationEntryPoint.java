package com.jjj.jjj.global.security.handler;

import java.io.IOException;

import com.jjj.jjj.global.exception.ErrorCode;
import com.jjj.jjj.global.exception.dto.ErrorDTO;
import com.jjj.jjj.global.response.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    // 인증이 되지 않음
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ApiResponse<ErrorDTO> errorResponse = ApiResponse.fail(ErrorDTO.from(ErrorCode.UNAUTHORIZED));

        response.getWriter().write(
                objectMapper.writeValueAsString(errorResponse)
        );
    }
}
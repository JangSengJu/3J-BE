package com.jjj.jjj.global.security.handler;

import java.io.IOException;

import com.jjj.jjj.global.exception.ErrorCode;
import com.jjj.jjj.global.exception.dto.ErrorDTO;
import com.jjj.jjj.global.response.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    // 인가가 되지 않음
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiResponse<ErrorDTO> errorResponse = ApiResponse.fail(ErrorDTO.from(ErrorCode.FORBIDDEN));

        response.getWriter().write(
                objectMapper.writeValueAsString(errorResponse)
        );
    }
}
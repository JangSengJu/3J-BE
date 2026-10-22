package com.jjj.jjj.domain.health.controller;

import com.jjj.jjj.domain.health.dto.response.HealthResponse;
import com.jjj.jjj.global.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final boolean groqConfigured;

    public HealthController(@Value("${groq.api-key:}") String groqApiKey) {
        this.groqConfigured = groqApiKey != null && !groqApiKey.isBlank();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.ok(new HealthResponse(true, groqConfigured)));
    }
}

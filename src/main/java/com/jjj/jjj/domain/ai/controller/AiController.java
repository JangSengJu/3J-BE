package com.jjj.jjj.domain.ai.controller;

import com.jjj.jjj.domain.ai.dto.request.RecommendRequest;
import com.jjj.jjj.domain.ai.dto.request.ReportRequest;
import com.jjj.jjj.domain.ai.dto.response.RecommendResponse;
import com.jjj.jjj.domain.ai.service.AiService;
import com.jjj.jjj.global.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/recommend")
    public ApiResponse<RecommendResponse> recommend(@RequestBody RecommendRequest request) {
        RecommendResponse response = aiService.recommend(request == null ? null : request.input());
        return ApiResponse.ok(response);
    }

    @PostMapping(value = "/report", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter report(@RequestBody ReportRequest request) {
        return aiService.report(request);
    }
}

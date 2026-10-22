package com.jjj.jjj.domain.ai.dto.response;

import com.jjj.jjj.domain.ai.dto.AiRecommendation;

import java.util.List;

public record RecommendResponse(
        List<AiRecommendation> recommendations,
        boolean fallback
) {
}

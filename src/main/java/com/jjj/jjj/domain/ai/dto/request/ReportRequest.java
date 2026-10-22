package com.jjj.jjj.domain.ai.dto.request;

import com.jjj.jjj.domain.ai.dto.AiInput;
import com.jjj.jjj.domain.ai.dto.AiRecommendation;

import java.util.List;

public record ReportRequest(
        AiInput input,
        List<AiRecommendation> recommendations
) {
}

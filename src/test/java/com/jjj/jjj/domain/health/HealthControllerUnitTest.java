package com.jjj.jjj.domain.health;

import com.jjj.jjj.domain.health.controller.HealthController;
import com.jjj.jjj.domain.health.dto.response.HealthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerUnitTest {

    @Test
    @DisplayName("Gemini 키가 설정되면 gemini=true를 반환한다")
    void geminiConfigured() {
        HealthResponse response = bodyOf(new HealthController("test-key"));

        assertThat(response.ok()).isTrue();
        assertThat(response.gemini()).isTrue();
    }

    @Test
    @DisplayName("Gemini 키가 없거나 비어 있으면 gemini=false를 반환한다")
    void geminiMissing() {
        assertThat(bodyOf(new HealthController("")).gemini()).isFalse();
        assertThat(bodyOf(new HealthController(null)).gemini()).isFalse();
    }

    private HealthResponse bodyOf(HealthController controller) {
        return controller.health().getBody().data();
    }
}

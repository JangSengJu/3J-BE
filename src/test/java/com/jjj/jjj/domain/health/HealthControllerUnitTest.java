package com.jjj.jjj.domain.health;

import com.jjj.jjj.domain.health.controller.HealthController;
import com.jjj.jjj.domain.health.dto.response.HealthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerUnitTest {

    @Test
    @DisplayName("Groq 키가 설정되면 groq=true를 반환한다")
    void groqConfigured() {
        HealthResponse response = bodyOf(new HealthController("test-key"));

        assertThat(response.ok()).isTrue();
        assertThat(response.groq()).isTrue();
    }

    @Test
    @DisplayName("Groq 키가 없거나 비어 있으면 groq=false를 반환한다")
    void groqMissing() {
        assertThat(bodyOf(new HealthController("")).groq()).isFalse();
        assertThat(bodyOf(new HealthController(null)).groq()).isFalse();
    }

    private HealthResponse bodyOf(HealthController controller) {
        return controller.health().getBody().data();
    }
}

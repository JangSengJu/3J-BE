package com.jjj.jjj.domain.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private String uniqueId() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@dsm.kr";
    }

    private String body(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode tree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("신규 사용자는 빈 예산 기본값을 조회한다")
    void getBudgetDefaults() throws Exception {
        String id = uniqueId();
        signUp(id);

        mockMvc.perform(get("/api/budget")
                        .header("Authorization", "Bearer " + accessToken(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.budget.resources").isEmpty())
                .andExpect(jsonPath("$.data.budget.limit").value(60))
                .andExpect(jsonPath("$.data.budget.notes").isEmpty())
                .andExpect(jsonPath("$.data.budget.purpose").value(""));
    }

    @Test
    @DisplayName("예산 저장 시 본문을 정규화하고 저장본을 반환한다")
    void saveBudgetNormalizes() throws Exception {
        String id = uniqueId();
        signUp(id);
        String token = accessToken(id);

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("name", "  web-server  ");
        resource.put("type", "EC2");
        resource.put("unit", "hour");
        resource.put("cost", 70.085);
        resource.put("usage", "<5%");
        resource.put("need", "대부분 유휴");
        resource.put("meta", "EC2 · m5.large · 730 시간");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resources", List.of(resource, Map.of("type", "EC2")));
        payload.put("limit", 0);
        payload.put("notes", Map.of("web", "웹 서버 메모"));
        payload.put("purpose", "  고객용 웹 서비스 운영  ");

        mockMvc.perform(put("/api/budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.budget.resources.length()").value(1))
                .andExpect(jsonPath("$.data.budget.resources[0].id").value("r-1"))
                .andExpect(jsonPath("$.data.budget.resources[0].name").value("web-server"))
                .andExpect(jsonPath("$.data.budget.resources[0].type").value("EC2"))
                .andExpect(jsonPath("$.data.budget.resources[0].unit").value("hour"))
                .andExpect(jsonPath("$.data.budget.resources[0].cost").value(70.09))
                .andExpect(jsonPath("$.data.budget.resources[0].usage").value("<5%"))
                .andExpect(jsonPath("$.data.budget.limit").value(60))
                .andExpect(jsonPath("$.data.budget.purpose").value("고객용 웹 서비스 운영"));

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/budget")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budget.resources.length()").value(1))
                .andExpect(jsonPath("$.data.budget.resources[0].name").value("web-server"))
                .andExpect(jsonPath("$.data.budget.limit").value(60))
                .andExpect(jsonPath("$.data.budget.notes.web").value("웹 서버 메모"))
                .andExpect(jsonPath("$.data.budget.purpose").value("고객용 웹 서비스 운영"));
    }

    @Test
    @DisplayName("리소스 누락 필드는 기본값으로 정규화한다")
    void saveBudgetAppliesResourceDefaults() throws Exception {
        String id = uniqueId();
        signUp(id);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resources", List.of(
                Map.of("name", "svc-a"),
                Map.of("name", "svc-b", "unit", "unknown-unit")
        ));

        mockMvc.perform(put("/api/budget")
                        .header("Authorization", "Bearer " + accessToken(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budget.resources.length()").value(2))
                .andExpect(jsonPath("$.data.budget.resources[0].id").value("r-1"))
                .andExpect(jsonPath("$.data.budget.resources[0].type").value("기타"))
                .andExpect(jsonPath("$.data.budget.resources[0].unit").value("month"))
                .andExpect(jsonPath("$.data.budget.resources[0].cost").value(0))
                .andExpect(jsonPath("$.data.budget.resources[0].usage").value("미상"))
                .andExpect(jsonPath("$.data.budget.resources[0].need").value("미상"))
                .andExpect(jsonPath("$.data.budget.resources[0].meta").value(""))
                .andExpect(jsonPath("$.data.budget.resources[1].id").value("r-2"))
                .andExpect(jsonPath("$.data.budget.resources[1].unit").value("month"));
    }

    @Test
    @DisplayName("limit이 양수가 아니면 기존 값을 유지한다")
    void saveBudgetKeepsExistingLimit() throws Exception {
        String id = uniqueId();
        signUp(id);
        String token = accessToken(id);

        save(token, Map.of("limit", 100));
        save(token, Map.of("limit", -5));

        mockMvc.perform(get("/api/budget")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budget.limit").value(100));
    }

    @Test
    @DisplayName("리소스는 최대 200건까지만 저장한다")
    void saveBudgetLimitsResources() throws Exception {
        String id = uniqueId();
        signUp(id);

        List<Map<String, Object>> resources = new ArrayList<>();
        for (int i = 0; i < 205; i++) {
            resources.add(Map.of("name", "resource-" + i));
        }

        mockMvc.perform(put("/api/budget")
                        .header("Authorization", "Bearer " + accessToken(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("resources", resources))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budget.resources.length()").value(200));
    }

    @Test
    @DisplayName("purpose, notes, meta는 상한 길이로 잘라 저장한다")
    void saveBudgetTruncatesLongText() throws Exception {
        String id = uniqueId();
        signUp(id);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", "x".repeat(1200));
        payload.put("notes", Map.of("note", "y".repeat(600)));
        payload.put("resources", List.of(Map.of("name", "svc", "meta", "z".repeat(300))));

        MvcResult result = mockMvc.perform(put("/api/budget")
                        .header("Authorization", "Bearer " + accessToken(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode budget = tree(result).get("data").get("budget");
        assertThat(budget.get("purpose").asString()).hasSize(1000);
        assertThat(budget.get("notes").get("note").asString()).hasSize(500);
        assertThat(budget.get("resources").get(0).get("meta").asString()).hasSize(200);
    }

    @Test
    @DisplayName("인증 없이 예산을 조회하거나 저장하면 401을 반환한다")
    void budgetUnauthorized() throws Exception {
        mockMvc.perform(get("/api/budget"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."));

        mockMvc.perform(put("/api/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("limit", 100))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."));
    }

    private void signUp(String id) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isCreated());
    }

    private String accessToken(String id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        return tree(result).get("data").get("accessToken").asString();
    }

    private void save(String token, Object payload) throws Exception {
        mockMvc.perform(put("/api/budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload)))
                .andExpect(status().isOk());
    }
}

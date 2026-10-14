package com.jjj.jjj.domain.auth;

import com.jjj.jjj.domain.auth.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

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
    @DisplayName("회원가입 성공 시 201과 정규화된 아이디를 반환한다")
    void signUp() throws Exception {
        String id = uniqueId();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.id").value(id))
                .andExpect(jsonPath("$.data.user.createdAt").exists());

        assertThat(memberRepository.findByUsername(id)).isPresent();
    }

    @Test
    @DisplayName("회원가입 시 아이디는 trim + 소문자로 정규화된다")
    void signUpNormalizesId() throws Exception {
        String raw = "  MixedCase-" + UUID.randomUUID().toString().substring(0, 6) + "@DSM.kr  ";
        String normalized = raw.trim().toLowerCase();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", raw, "password", "secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.id").value(normalized));

        assertThat(memberRepository.findByUsername(normalized)).isPresent();
    }

    @Test
    @DisplayName("이미 가입된 아이디면 409를 반환한다")
    void signUpDuplicated() throws Exception {
        String id = uniqueId();
        String payload = body(Map.of("id", id, "password", "secret123"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("이미 가입된 아이디입니다."));
    }

    @Test
    @DisplayName("아이디/비밀번호 형식이 잘못되면 400을 반환한다")
    void signUpInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", "ab", "password", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("trim/소문자 정규화 후 4자 미만이 되는 아이디는 400을 반환한다")
    void signUpTooShortAfterNormalization() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", "  Ab  ", "password", "secret123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("로그인 성공 시 Access Token과 Refresh 쿠키를 발급한다")
    void signIn() throws Exception {
        String id = uniqueId();
        signUp(id);

        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").value(id))
                .andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("SameSite=Strict").contains("HttpOnly");
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 401을 반환한다")
    void signInWrongPassword() throws Exception {
        String id = uniqueId();
        signUp(id);

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("Refresh 쿠키로 Access Token을 재발급하고 토큰을 회전한다")
    void refresh() throws Exception {
        String id = uniqueId();
        signUp(id);
        Cookie refreshCookie = signIn(id);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie rotated = result.getResponse().getCookie("refreshToken");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(refreshCookie.getValue());
    }

    @Test
    @DisplayName("이미 회전(소비)된 Refresh 토큰을 재사용하면 401을 반환한다")
    void refreshReuseRotatedToken() throws Exception {
        String id = uniqueId();
        signUp(id);
        Cookie refreshCookie = signIn(id);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."));
    }

    @Test
    @DisplayName("Refresh 쿠키가 없으면 401을 반환한다")
    void refreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."));
    }

    @Test
    @DisplayName("로그아웃하면 Refresh 토큰이 폐기되어 재발급이 실패한다")
    void signOut() throws Exception {
        String id = uniqueId();
        signUp(id);
        Cookie refreshCookie = signIn(id);
        String accessToken = accessToken(id);

        mockMvc.perform(post("/api/auth/signout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access 토큰이 만료돼도 Refresh 쿠키만으로 로그아웃할 수 있다")
    void signOutWithoutAccessToken() throws Exception {
        String id = uniqueId();
        signUp(id);
        Cookie refreshCookie = signIn(id);

        mockMvc.perform(post("/api/auth/signout")
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    private void signUp(String id) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isCreated());
    }

    private Cookie signIn(String id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("refreshToken");
    }

    private String accessToken(String id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        return tree(result).get("data").get("accessToken").asString();
    }
}

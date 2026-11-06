package com.back.was.domain.user;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** USER-01 내 정보 조회 · USER-02 내 정보 수정. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApiTest {

    private static final String EMAIL = "hong@email.com";
    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    private String accessToken;

    @BeforeEach
    void signUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        accessToken = JsonPath.read(body, "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private MockHttpServletRequestBuilder patchMe(String json) {
        return authed(patch("/users/me").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("USER-01: 토큰의 주인 정보를 id·username·email·role·createdAt 으로 준다")
    void getMeReturnsOwnProfile() throws Exception {
        mockMvc.perform(authed(get("/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username").value("홍길동"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("USER-01: createdAt 은 명세 0장 2)의 시간대 포함 ISO 8601 (+09:00) 이다")
    void createdAtCarriesKstOffset() throws Exception {
        mockMvc.perform(authed(get("/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt",
                        Matchers.matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$")));
    }

    @Test
    @DisplayName("USER-01: 비밀번호 해시는 응답에 절대 들어가지 않는다")
    void responseNeverLeaksPasswordHash() throws Exception {
        String body = mockMvc.perform(authed(get("/users/me")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("password").doesNotContain("$2a$");
    }

    @Test
    @DisplayName("USER-01: 토큰 없이 호출하면 401 UNAUTHORIZED")
    void getMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("USER-02: username 을 보내면 수정되고 USER-01 과 같은 모양으로 응답한다")
    void updateUsername() throws Exception {
        mockMvc.perform(patchMe("""
                        {"username": "김철수"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("김철수"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getUsername()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("USER-02: 보낸 필드만 수정 — 빈 객체면 아무것도 바뀌지 않는다")
    void emptyPatchChangesNothing() throws Exception {
        mockMvc.perform(patchMe("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("홍길동"));

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getUsername()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("USER-02: 명세에 없는 필드(email·role)는 PATCH 로 바뀌지 않는다")
    void unspecifiedFieldsAreNotMutable() throws Exception {
        mockMvc.perform(patchMe("""
                        {"username": "김철수", "email": "attacker@email.com", "role": "ADMIN"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"));

        var saved = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("USER-02: 50자를 넘는 username 은 400 VALIDATION_ERROR")
    void tooLongUsernameIsRejected() throws Exception {
        mockMvc.perform(patchMe("""
                        {"username": "%s"}
                        """.formatted("가".repeat(51))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'username')]").exists());
    }

    @Test
    @DisplayName("USER-02: 빈 문자열·공백만인 username 은 400 — null(미전송)과 구분된다")
    void blankUsernameIsRejected() throws Exception {
        mockMvc.perform(patchMe("""
                        {"username": ""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patchMe("""
                        {"username": "   "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("USER-02: 토큰 없이 호출하면 401")
    void updateRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"김철수\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("다른 사용자의 토큰은 그 사용자 정보만 본다 — /users/me 는 항상 토큰 주인 기준")
    void meIsScopedToTokenOwner() throws Exception {
        String otherBody = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "이영희", "email": "lee@email.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String otherToken = JsonPath.read(otherBody, "$.data.accessToken");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("lee@email.com"))
                .andExpect(jsonPath("$.data.username").value("이영희"));
    }
}

package com.back.was.domain.dev;

import com.back.was.domain.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /dev/**} 가 <b>dev 프로필 밖에서는 아예 없다</b>는 것 — CLAUDE.md 규칙 ·
 * 서버_내부_처리 12장 ("운영 환경에서는 {@code /dev/**} 비활성화").
 *
 * <p>프로필을 지정하지 않았으므로 기본 컨텍스트다. 운영 배포에서 이 API 가 열려 있으면
 * 누구나 자기 챌린지를 즉시 종료시킬 수 있고, DEV-01 이 생기면 데이터 삭제까지 가능해진다 —
 * 그래서 "등록되지 않는다"를 테스트로 고정한다.
 *
 * <p>동작하는 쪽은 {@code DevChallengeApiTest} 가 {@code @ActiveProfiles("dev")} 로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DevProfileOnlyTest {

    private static final String EMAIL = "dev-profile@email.com";
    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationContext applicationContext;

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

    @Test
    @DisplayName("dev 프로필이 아니면 컨트롤러·서비스 빈이 하나도 없다")
    void devBeansAreNotRegistered() {
        assertThat(applicationContext.getBeanNamesForType(DevChallengeController.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(DevChallengeService.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(DevSeedController.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(DevSeedService.class)).isEmpty();
    }

    @Test
    @DisplayName("DEV-02 경로는 404 다 — 인증된 요청이어도 매핑 자체가 없다")
    void devChallengeEndpointIsNotMapped() throws Exception {
        assertThat(userRepository.findByEmail(EMAIL)).isPresent();

        mockMvc.perform(post("/dev/challenges/{id}/complete", 1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DEV-01 경로도 404 다 — 운영에 열리면 누구나 자기 데이터를 날릴 수 있다")
    void devSeedEndpointIsNotMapped() throws Exception {
        mockMvc.perform(post("/dev/seed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\": \"NORMAL\"}"))
                .andExpect(status().isNotFound());
    }

    /**
     * {@code DemoTransactionPlanner} 는 프로필과 무관하게 등록된다 — DB 를 모르는 순수
     * 계산이라 운영에 있어도 아무 일도 하지 않고, {@code @Profile} 을 붙이면 dev 테스트에서
     * 단독으로 쓰기 어려워진다. 데이터를 건드리는 쪽(서비스·컨트롤러)만 막는 것이 요점이다.
     */
    @Test
    @DisplayName("거래 계획기는 프로필과 무관하다 — 데이터를 건드리지 않는다")
    void thePlannerIsProfileIndependent() {
        assertThat(applicationContext.getBeanNamesForType(DemoTransactionPlanner.class))
                .isNotEmpty();
    }
}

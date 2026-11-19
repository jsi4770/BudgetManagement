package com.back.was.domain.dev;

import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.Gender;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.KstTime;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DEV-03 — 로그인한 계정의 데이터를 시연 계정(dummy123)과 같은 상태로 초기화한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DemoAccountResetApiTest {

    private static final String EMAIL = "dev03@email.com";
    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void signUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "발표자", "email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(body, "$.data.accessToken");
    }

    private ResultActions reset(String bearer) throws Exception {
        var request = post("/dev/demo-account/reset");
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        return mockMvc.perform(request);
    }

    private long transactionsOf(Long userId) {
        return transactionRepository.findAll().stream().filter(tx -> tx.getUser().getId().equals(userId)).count();
    }

    @Test
    @DisplayName("내 온보딩을 천안·25세·선택 안 함으로, 데이터를 DELIVERY_SPIKE 로 — 응답은 DEV-01 과 같다")
    void resetsMyAccountLikeTheDemoAccount() throws Exception {
        reset(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenario").value("DELIVERY_SPIKE"))
                .andExpect(jsonPath("$.data.nudgeCreated").value(true));

        User me = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(me.getRegionCode()).isEqualTo("44130");
        assertThat(me.ageIn(KstTime.today().getYear())).isEqualTo(25);
        assertThat(me.getGender()).isEqualTo(Gender.NONE);
        assertThat(transactionsOf(me.getId())).isPositive();
    }

    @Test
    @DisplayName("닉네임·비밀번호는 그대로 — 데이터만 초기화한다")
    void keepsMyCredentials() throws Exception {
        reset(token).andExpect(status().isOk());

        User me = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(me.getUsername()).isEqualTo("발표자");
        assertThat(passwordEncoder.matches(PASSWORD, me.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("두 번 불러도 같은 건수 — 기존 데이터를 지우고 다시 깐다")
    void isRepeatable() throws Exception {
        reset(token).andExpect(status().isOk());
        Long id = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        long first = transactionsOf(id);

        reset(token).andExpect(status().isOk());

        assertThat(transactionsOf(id)).isEqualTo(first);
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void requiresLogin() throws Exception {
        reset(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}

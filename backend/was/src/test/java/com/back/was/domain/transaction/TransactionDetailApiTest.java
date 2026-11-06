package com.back.was.domain.transaction;

import com.back.was.global.support.KstTime;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TX-04 거래 상세. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionDetailApiTest {

    /** ERD 4장 시드: 2 = 배달(EXPENSE). */
    private static final long DELIVERY = 2L;

    private static final LocalDate TODAY = KstTime.today();

    @Autowired
    MockMvc mockMvc;

    private String accessToken;

    @BeforeEach
    void signUp() throws Exception {
        accessToken = signUp("tx-detail@email.com");
    }

    private String signUp(String email) throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "password123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private long given(String memo) throws Exception {
        String body = mockMvc.perform(authed(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": 24000, "transactionDate": "%s", "memo": "%s"}
                                """.formatted(DELIVERY, TODAY, memo))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    @Test
    @DisplayName("TX-04: TX-01 과 같은 모양 — createdAt 까지 포함해서 준다")
    void returnsSameShapeAsCreate() throws Exception {
        long id = given("치킨");

        mockMvc.perform(authed(get("/transactions/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value((int) id))
                .andExpect(jsonPath("$.data.category.id").value(2))
                .andExpect(jsonPath("$.data.category.name").value("배달"))
                .andExpect(jsonPath("$.data.category.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.amount").value(24000))
                .andExpect(jsonPath("$.data.transactionDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.memo").value("치킨"))
                .andExpect(jsonPath("$.data.createdAt",
                        Matchers.matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$")));
    }

    @Test
    @DisplayName("TX-04: 없는 id 는 404 NOT_FOUND")
    void unknownIdIsNotFound() throws Exception {
        mockMvc.perform(authed(get("/transactions/{id}", 999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("TX-04: 다른 사용자의 거래는 403 이 아니라 404 — 존재 여부를 알려주지 않는다 (명세 0장 5))")
    void otherUsersTransactionIsNotFound() throws Exception {
        long mine = given("내거래");

        accessToken = signUp("tx-detail-other@email.com");

        mockMvc.perform(authed(get("/transactions/{id}", mine)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        // 존재하지 않는 ID 와 응답이 완전히 같아야 구분이 불가능하다.
        String existing = mockMvc.perform(authed(get("/transactions/{id}", mine)))
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(authed(get("/transactions/{id}", 999999)))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(existing).isEqualTo(missing);
    }

    @Test
    @DisplayName("TX-04: 숫자가 아닌 id 는 400 VALIDATION_ERROR")
    void malformedIdIsRejected() throws Exception {
        mockMvc.perform(authed(get("/transactions/{id}", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TX-03 회귀: /transactions/calendar 는 TX-04 의 {id} 로 해석되지 않는다")
    void calendarPathStillWins() throws Exception {
        mockMvc.perform(authed(get("/transactions/calendar")
                        .param("yearMonth", TODAY.toString().substring(0, 7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").isArray());
    }

    @Test
    @DisplayName("TX-04: 토큰 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        long id = given("치킨");

        mockMvc.perform(get("/transactions/{id}", id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}

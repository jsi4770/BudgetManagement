package com.back.was.domain.transaction;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TX-06 거래 삭제. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionDeleteApiTest {

    /** ERD 4장 시드: 2 = 배달(EXPENSE). */
    private static final long DELIVERY = 2L;

    private static final LocalDate TODAY = KstTime.today();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionRepository transactionRepository;

    private String accessToken;
    private long transactionId;

    @BeforeEach
    void setUp() throws Exception {
        accessToken = signUp("tx-delete@email.com");
        transactionId = given("치킨");
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
    @DisplayName("TX-06: 204 No Content 를 바디 없이 주고 행이 실제로 지워진다")
    void deletesTransaction() throws Exception {
        String body = mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEmpty();
        assertThat(transactionRepository.findById(transactionId)).isEmpty();
    }

    @Test
    @DisplayName("TX-06: 삭제한 거래는 TX-04 상세·TX-02 목록에서 사라진다")
    void deletedTransactionDisappearsFromReads() throws Exception {
        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/transactions/{id}", transactionId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mockMvc.perform(authed(get("/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    @DisplayName("TX-06: 다른 거래는 영향을 받지 않는다")
    void deletesOnlyTheTargetRow() throws Exception {
        long keep = given("남을 거래");

        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNoContent());

        assertThat(transactionRepository.findById(keep)).isPresent();
        mockMvc.perform(authed(get("/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].memo").value("남을 거래"));
    }

    @Test
    @DisplayName("TX-06: 두 번 삭제하면 두 번째는 404 — 멱등하지 않다 (AUTH-04 와 다른 선택)")
    void secondDeleteIsNotFound() throws Exception {
        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("TX-06: 없는 id 는 404")
    void unknownIdIsNotFound() throws Exception {
        mockMvc.perform(authed(delete("/transactions/{id}", 999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("TX-06: 다른 사용자의 거래는 403 이 아니라 404 이고, 지워지지 않는다")
    void otherUsersTransactionCannotBeDeleted() throws Exception {
        accessToken = signUp("tx-delete-other@email.com");

        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(transactionRepository.findById(transactionId)).isPresent();
    }

    @Test
    @DisplayName("TX-06: 숫자가 아닌 id 는 400")
    void malformedIdIsRejected() throws Exception {
        mockMvc.perform(authed(delete("/transactions/{id}", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TX-06: 토큰 없이 호출하면 401 이고 지워지지 않는다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/transactions/{id}", transactionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        assertThat(transactionRepository.findById(transactionId)).isPresent();
    }
}

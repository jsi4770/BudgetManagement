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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TX-05 거래 수정. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionUpdateApiTest {

    /** ERD 4장 시드: 1 = 식비(EXPENSE), 2 = 배달(EXPENSE), 7 = 급여(INCOME). */
    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long SALARY = 7L;

    private static final LocalDate TODAY = KstTime.today();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionRepository transactionRepository;

    private String accessToken;
    private long transactionId;

    @BeforeEach
    void setUp() throws Exception {
        accessToken = signUp("tx-update@email.com");
        transactionId = given(DELIVERY, 24000, TODAY, "치킨");
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

    private long given(long categoryId, int amount, LocalDate date, String memo) throws Exception {
        String body = mockMvc.perform(authed(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": %d, "transactionDate": "%s", "memo": "%s"}
                                """.formatted(categoryId, amount, date, memo))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private MockHttpServletRequestBuilder patchTransaction(long id, String json) {
        return authed(patch("/transactions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("TX-05: 명세 예시대로 amount·memo 만 보내면 그 둘만 바뀌고 TX-01 모양으로 응답한다")
    void updatesOnlySentFields() throws Exception {
        mockMvc.perform(patchTransaction(transactionId, """
                        {"amount": 22000, "memo": "치킨 (쿠폰 할인)"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value((int) transactionId))
                .andExpect(jsonPath("$.data.amount").value(22000))
                .andExpect(jsonPath("$.data.memo").value("치킨 (쿠폰 할인)"))
                // 보내지 않은 필드는 그대로다.
                .andExpect(jsonPath("$.data.category.id").value(2))
                .andExpect(jsonPath("$.data.transactionDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.createdAt",
                        Matchers.matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$")));

        Transaction saved = transactionRepository.findById(transactionId).orElseThrow();
        assertThat(saved.getAmount()).isEqualTo(22000);
        assertThat(saved.getMemo()).isEqualTo("치킨 (쿠폰 할인)");
        assertThat(saved.getCategory().getId()).isEqualTo(DELIVERY);
    }

    @Test
    @DisplayName("TX-05: 빈 객체면 아무것도 바뀌지 않고 현재 값을 돌려준다")
    void emptyPatchChangesNothing() throws Exception {
        mockMvc.perform(patchTransaction(transactionId, "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(24000))
                .andExpect(jsonPath("$.data.memo").value("치킨"))
                .andExpect(jsonPath("$.data.category.id").value(2));
    }

    @Test
    @DisplayName("TX-05: categoryId 를 바꾸면 카테고리 객체가 함께 바뀐다")
    void updatesCategory() throws Exception {
        mockMvc.perform(patchTransaction(transactionId, """
                        {"categoryId": 1}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category.id").value(1))
                .andExpect(jsonPath("$.data.category.name").value("식비"))
                .andExpect(jsonPath("$.data.category.type").value("EXPENSE"));

        assertThat(transactionRepository.findById(transactionId).orElseThrow()
                .getCategory().getId()).isEqualTo(FOOD);
    }

    @Test
    @DisplayName("TX-05: 수입 카테고리로 바꾸면 거래의 방향도 뒤집힌다 (금지 규칙이 없다)")
    void categoryChangeCanFlipDirection() throws Exception {
        mockMvc.perform(patchTransaction(transactionId, """
                        {"categoryId": 7}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category.id").value(7))
                .andExpect(jsonPath("$.data.category.type").value("INCOME"));

        assertThat(transactionRepository.findById(transactionId).orElseThrow()
                .getCategory().getId()).isEqualTo(SALARY);
    }

    @Test
    @DisplayName("TX-05: transactionDate 를 과거로 바꿀 수 있다")
    void updatesTransactionDate() throws Exception {
        LocalDate past = TODAY.minusDays(3);

        mockMvc.perform(patchTransaction(transactionId, """
                        {"transactionDate": "%s"}
                        """.formatted(past)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionDate").value(past.toString()));

        assertThat(transactionRepository.findById(transactionId).orElseThrow()
                .getTransactionDate()).isEqualTo(past);
    }

    @Test
    @DisplayName("TX-05: 수정으로도 미래 날짜는 안 된다 — 400 + reason FUTURE_DATE")
    void futureDateIsRejectedOnUpdate() throws Exception {
        mockMvc.perform(patchTransaction(transactionId, """
                        {"transactionDate": "%s"}
                        """.formatted(TODAY.plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'transactionDate')].reason")
                        .value(Matchers.hasItem("FUTURE_DATE")));

        assertThat(transactionRepository.findById(transactionId).orElseThrow()
                .getTransactionDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("TX-05: 메모는 빈 문자열을 보내 지운다 — null(미전송)은 '유지'다")
    void blankMemoClearsItWhileNullKeepsIt() throws Exception {
        mockMvc.perform(patchTransaction(transactionId, """
                        {"memo": ""}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value(Matchers.nullValue()));
        assertThat(transactionRepository.findById(transactionId).orElseThrow().getMemo()).isNull();

        long other = given(DELIVERY, 10000, TODAY, "유지될 메모");
        mockMvc.perform(patchTransaction(other, """
                        {"memo": null, "amount": 11000}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(11000))
                .andExpect(jsonPath("$.data.memo").value("유지될 메모"));
    }

    @Test
    @DisplayName("TX-05: 0원·음수는 400, 255자 초과 memo 도 400")
    void invalidValuesAreRejected() throws Exception {
        for (String amount : new String[]{"0", "-1"}) {
            mockMvc.perform(patchTransaction(transactionId, """
                            {"amount": %s}
                            """.formatted(amount)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        mockMvc.perform(patchTransaction(transactionId, """
                        {"memo": "%s"}
                        """.formatted("가".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field == 'memo')]").exists());

        assertThat(transactionRepository.findById(transactionId).orElseThrow().getAmount())
                .isEqualTo(24000);
    }

    @Test
    @DisplayName("TX-05: 없는 카테고리로 바꾸려 하면 404 NOT_FOUND 이고 아무것도 바뀌지 않는다")
    void unknownCategoryIsNotFound() throws Exception {
        mockMvc.perform(patchTransaction(transactionId, """
                        {"categoryId": 999, "amount": 1000}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        Transaction saved = transactionRepository.findById(transactionId).orElseThrow();
        assertThat(saved.getAmount()).isEqualTo(24000);
        assertThat(saved.getCategory().getId()).isEqualTo(DELIVERY);
    }

    @Test
    @DisplayName("TX-05: 없는 거래는 404")
    void unknownTransactionIsNotFound() throws Exception {
        mockMvc.perform(patchTransaction(999999, """
                        {"amount": 1000}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("TX-05: 다른 사용자의 거래는 403 이 아니라 404 이고, 수정되지 않는다")
    void otherUsersTransactionCannotBeUpdated() throws Exception {
        accessToken = signUp("tx-update-other@email.com");

        mockMvc.perform(patchTransaction(transactionId, """
                        {"amount": 1}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(transactionRepository.findById(transactionId).orElseThrow().getAmount())
                .isEqualTo(24000);
    }

    @Test
    @DisplayName("TX-05: 토큰 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/transactions/{id}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}

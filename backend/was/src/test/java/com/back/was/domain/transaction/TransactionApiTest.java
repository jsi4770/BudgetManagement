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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TX-01 거래 등록. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionApiTest {

    /** ERD 4장 시드: 2 = 배달(EXPENSE), 7 = 급여(INCOME). */
    private static final long DELIVERY = 2L;
    private static final long SALARY = 7L;

    private static final LocalDate TODAY = KstTime.today();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionRepository transactionRepository;

    private String accessToken;

    @BeforeEach
    void signUp() throws Exception {
        accessToken = signUp("tx@email.com");
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

    private MockHttpServletRequestBuilder createTransaction(String json) {
        return post("/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    @Test
    @DisplayName("TX-01: 201 과 함께 id·category 객체·amount·transactionDate·memo·createdAt 을 준다")
    void createsTransaction() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s", "memo": "치킨"}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.category.id").value(2))
                .andExpect(jsonPath("$.data.category.name").value("배달"))
                .andExpect(jsonPath("$.data.category.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.amount").value(24000))
                .andExpect(jsonPath("$.data.transactionDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.memo").value("치킨"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        assertThat(transactionRepository.findAll()).hasSize(1);
        Transaction saved = transactionRepository.findAll().getFirst();
        assertThat(saved.getAmount()).isEqualTo(24000);
        assertThat(saved.getCategory().getId()).isEqualTo(DELIVERY);
        assertThat(saved.getTransactionDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("TX-01: createdAt 은 명세 0장 2)의 오프셋 포함 ISO 8601, transactionDate 는 날짜만")
    void dateFormatsFollowSpec() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s"}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdAt",
                        Matchers.matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$")))
                .andExpect(jsonPath("$.data.transactionDate",
                        Matchers.matchesPattern("^\\d{4}-\\d{2}-\\d{2}$")));
    }

    @Test
    @DisplayName("TX-01: 수입/지출은 요청이 아니라 카테고리 TYPE 으로 결정된다 (급여 → INCOME)")
    void directionComesFromCategoryType() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 7, "amount": 2500000, "transactionDate": "%s", "memo": "월급"}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category.name").value("급여"))
                .andExpect(jsonPath("$.data.category.type").value("INCOME"))
                .andExpect(jsonPath("$.data.amount").value(2500000));

        assertThat(transactionRepository.findAll().getFirst().getCategory().getId()).isEqualTo(SALARY);
    }

    @Test
    @DisplayName("TX-01: memo 는 선택 — 미전송이면 null, 공백만이면 null 로 저장된다")
    void memoIsOptional() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s"}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memo").value(Matchers.nullValue()));

        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s", "memo": "   "}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memo").value(Matchers.nullValue()));

        assertThat(transactionRepository.findAll())
                .allSatisfy(transaction -> assertThat(transaction.getMemo()).isNull());
    }

    @Test
    @DisplayName("TX-01: 오늘(KST)은 허용된다 — 경계값")
    void todayIsAllowed() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 1, "transactionDate": "%s"}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TX-01: 미래 날짜는 400 VALIDATION_ERROR + reason FUTURE_DATE (명세 TX-01)")
    void futureDateIsRejected() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s"}
                        """.formatted(TODAY.plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'transactionDate')].reason")
                        .value(Matchers.hasItem("FUTURE_DATE")));

        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("TX-01: 과거 날짜는 허용된다 (영수증·누락 입력)")
    void pastDateIsAllowed() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s"}
                        """.formatted(TODAY.minusDays(30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transactionDate").value(TODAY.minusDays(30).toString()));
    }

    @Test
    @DisplayName("TX-01: 없는 카테고리는 404 NOT_FOUND (명세 TX-01 Errors)")
    void unknownCategoryIsNotFound() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 999, "amount": 24000, "transactionDate": "%s"}
                        """.formatted(TODAY)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("TX-01: categoryId·amount·transactionDate 누락은 400 REQUIRED")
    void requiredFieldsAreValidated() throws Exception {
        mockMvc.perform(createTransaction("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'categoryId')].reason")
                        .value(Matchers.hasItem("REQUIRED")))
                .andExpect(jsonPath("$.error.details[?(@.field == 'amount')].reason")
                        .value(Matchers.hasItem("REQUIRED")))
                .andExpect(jsonPath("$.error.details[?(@.field == 'transactionDate')].reason")
                        .value(Matchers.hasItem("REQUIRED")));
    }

    @Test
    @DisplayName("TX-01: 0원·음수는 400 (명세 1 이상)")
    void amountMustBeAtLeastOne() throws Exception {
        for (String amount : new String[]{"0", "-1"}) {
            mockMvc.perform(createTransaction("""
                            {"categoryId": 2, "amount": %s, "transactionDate": "%s"}
                            """.formatted(amount, TODAY)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    @DisplayName("TX-01: 255자를 넘는 memo 는 400")
    void tooLongMemoIsRejected() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s", "memo": "%s"}
                        """.formatted(TODAY, "가".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field == 'memo')]").exists());
    }

    @Test
    @DisplayName("TX-01: 날짜 형식이 YYYY-MM-DD 가 아니면 400")
    void malformedDateIsRejected() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "2026/11/04"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TX-01: 거래는 토큰 주인에게 귀속된다 — 사용자 ID 는 요청으로 받지 않는다")
    void transactionBelongsToTokenOwner() throws Exception {
        String body = mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s"}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstUserTxId = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        String firstUserId = String.valueOf(
                transactionRepository.findById(firstUserTxId).orElseThrow().getUser().getId());

        accessToken = signUp("tx-other@email.com");
        String otherBody = mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 1000, "transactionDate": "%s", "userId": 1}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long otherTxId = ((Number) JsonPath.read(otherBody, "$.data.id")).longValue();

        String otherUserId = String.valueOf(
                transactionRepository.findById(otherTxId).orElseThrow().getUser().getId());

        assertThat(otherUserId).isNotEqualTo(firstUserId);
    }

    @Test
    @DisplayName("TX-01: 토큰 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": 2, "amount": 24000, "transactionDate": "%s"}
                                """.formatted(TODAY)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        assertThat(transactionRepository.findAll()).isEmpty();
    }
}

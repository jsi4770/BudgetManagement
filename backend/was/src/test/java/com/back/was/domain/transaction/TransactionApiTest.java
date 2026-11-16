package com.back.was.domain.transaction;

import com.back.was.global.support.KstTime;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TX-01 거래 등록. 한 건 규칙은 {@link #createTransaction} 이 한 건을 배열로 감싸 확인하고, 여러 건 규칙은 {@link Multiple}. */
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
                .content("{\"transactions\": [" + json + "]}");
    }

    @Test
    @DisplayName("TX-01: 201 과 함께 id·category 객체·amount·transactionDate·memo·createdAt 을 준다")
    void createsTransaction() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s", "memo": "치킨"}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].category.id").value(2))
                .andExpect(jsonPath("$.data[0].category.name").value("배달"))
                .andExpect(jsonPath("$.data[0].category.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data[0].amount").value(24000))
                .andExpect(jsonPath("$.data[0].transactionDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data[0].memo").value("치킨"))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty());

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
                .andExpect(jsonPath("$.data[0].createdAt",
                        Matchers.matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$")))
                .andExpect(jsonPath("$.data[0].transactionDate",
                        Matchers.matchesPattern("^\\d{4}-\\d{2}-\\d{2}$")));
    }

    @Test
    @DisplayName("TX-01: 수입/지출은 요청이 아니라 카테고리 TYPE 으로 결정된다 (급여 → INCOME)")
    void directionComesFromCategoryType() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 7, "amount": 2500000, "transactionDate": "%s", "memo": "월급"}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].category.name").value("급여"))
                .andExpect(jsonPath("$.data[0].category.type").value("INCOME"))
                .andExpect(jsonPath("$.data[0].amount").value(2500000));

        assertThat(transactionRepository.findAll().getFirst().getCategory().getId()).isEqualTo(SALARY);
    }

    @Test
    @DisplayName("TX-01: memo 는 선택 — 미전송이면 null, 공백만이면 null 로 저장된다")
    void memoIsOptional() throws Exception {
        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s"}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].memo").value(Matchers.nullValue()));

        mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 24000, "transactionDate": "%s", "memo": "   "}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].memo").value(Matchers.nullValue()));

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
                .andExpect(jsonPath("$.error.details[?(@.field == 'transactions[0].transactionDate')].reason")
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
                .andExpect(jsonPath("$.data[0].transactionDate").value(TODAY.minusDays(30).toString()));
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
                .andExpect(jsonPath("$.error.details[?(@.field == 'transactions[0].categoryId')].reason")
                        .value(Matchers.hasItem("REQUIRED")))
                .andExpect(jsonPath("$.error.details[?(@.field == 'transactions[0].amount')].reason")
                        .value(Matchers.hasItem("REQUIRED")))
                .andExpect(jsonPath("$.error.details[?(@.field == 'transactions[0].transactionDate')].reason")
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
                .andExpect(jsonPath("$.error.details[?(@.field == 'transactions[0].memo')]").exists());
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
        long firstUserTxId = ((Number) JsonPath.read(body, "$.data[0].id")).longValue();

        String firstUserId = String.valueOf(
                transactionRepository.findById(firstUserTxId).orElseThrow().getUser().getId());

        accessToken = signUp("tx-other@email.com");
        String otherBody = mockMvc.perform(createTransaction("""
                        {"categoryId": 2, "amount": 1000, "transactionDate": "%s", "userId": 1}
                        """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long otherTxId = ((Number) JsonPath.read(otherBody, "$.data[0].id")).longValue();

        String otherUserId = String.valueOf(
                transactionRepository.findById(otherTxId).orElseThrow().getUser().getId());

        assertThat(otherUserId).isNotEqualTo(firstUserId);
    }

    /** 본문을 그대로 보낸다 — {@link #createTransaction} 처럼 한 건을 배열로 감싸지 않는다. */
    private MockHttpServletRequestBuilder createTransactions(String body) {
        return post("/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String item(long categoryId, int amount, LocalDate date) {
        return """
                {"categoryId": %d, "amount": %d, "transactionDate": "%s"}""".formatted(categoryId, amount, date);
    }

    @Nested
    @DisplayName("TX-01 여러 건")
    class Multiple {

        @Test
        @DisplayName("여러 건을 한 번에 저장하고, 응답은 요청 순서 그대로다")
        void savesAllInRequestOrder() throws Exception {
            mockMvc.perform(createTransactions("""
                            {"transactions": [%s, %s, %s]}
                            """.formatted(item(DELIVERY, 24000, TODAY), item(SALARY, 2500000, TODAY.minusDays(3)),
                            item(DELIVERY, 4500, TODAY.minusDays(1)))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0].amount").value(24000))
                    .andExpect(jsonPath("$.data[1].amount").value(2500000))
                    .andExpect(jsonPath("$.data[1].category.type").value("INCOME"))
                    .andExpect(jsonPath("$.data[2].amount").value(4500))
                    .andExpect(jsonPath("$.data[*].id").value(Matchers.everyItem(Matchers.notNullValue())));

            assertThat(transactionRepository.findAll()).hasSize(3);
        }

        @Test
        @DisplayName("한 건이라도 규칙을 어기면 400 이고 아무것도 저장하지 않는다 — field 에 몇 번째인지 담긴다")
        void oneInvalidRejectsAll() throws Exception {
            mockMvc.perform(createTransactions("""
                            {"transactions": [%s, %s]}
                            """.formatted(item(DELIVERY, 24000, TODAY), item(DELIVERY, 1000, TODAY.plusDays(1)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[?(@.field == 'transactions[1].transactionDate')].reason")
                            .value(Matchers.hasItem("FUTURE_DATE")));

            assertThat(transactionRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("없는 카테고리가 하나라도 있으면 404 이고 앞의 거래도 저장하지 않는다")
        void oneMissingCategoryRejectsAll() throws Exception {
            mockMvc.perform(createTransactions("""
                            {"transactions": [%s, %s]}
                            """.formatted(item(DELIVERY, 24000, TODAY), item(999L, 1000, TODAY))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

            assertThat(transactionRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("빈 배열·transactions 누락·null 원소는 400")
        void emptyOrMissingIsBadRequest() throws Exception {
            for (String body : new String[]{"{\"transactions\": []}", "{}", "{\"transactions\": [null]}"}) {
                mockMvc.perform(createTransactions(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
            }
        }

        @Test
        @DisplayName("50건까지는 받고 51건은 400")
        void atMostFifty() throws Exception {
            String one = item(DELIVERY, 1000, TODAY);

            mockMvc.perform(createTransactions("{\"transactions\": [" + String.join(",", Collections.nCopies(51, one)) + "]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[?(@.field == 'transactions')]").exists());
            assertThat(transactionRepository.findAll()).isEmpty();

            mockMvc.perform(createTransactions("{\"transactions\": [" + String.join(",", Collections.nCopies(50, one)) + "]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.length()").value(50));
        }

        @Test
        @DisplayName("예전처럼 거래 객체 하나를 그대로 보내면 400 — transactions 가 없다")
        void bareObjectIsRejected() throws Exception {
            mockMvc.perform(createTransactions(item(DELIVERY, 24000, TODAY)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[?(@.field == 'transactions')].reason")
                            .value(Matchers.hasItem("REQUIRED")));
        }
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

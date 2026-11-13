package com.back.was.domain.transaction;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.domain.ai.TextParseCommand;
import com.back.was.domain.ai.TransactionTextParser;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.back.was.global.support.KstTime;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TX-07 자연어 분석.
 *
 * <p>실제 Gemini 호출 없이 {@link TransactionTextParser} 포트를 대체해 백엔드 책임만
 * 검증한다 — 프롬프트·스키마는 서버_내부_처리 8장대로 AI 담당 몫이고, 여기서 고정해야
 * 하는 건 명세 TX-07 의 응답 계약과 실패 코드다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionParseApiTest {

    private static final LocalDate TODAY = KstTime.today();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransactionTextParser transactionTextParser;

    private String accessToken;

    @BeforeEach
    void signUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "tx-parse@email.com", "password": "password123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        accessToken = JsonPath.read(body, "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder parse(String text) {
        return post("/transactions/parse")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"text": "%s"}
                        """.formatted(text));
    }

    private void parserReturns(ParsedTransaction parsed) {
        Mockito.when(transactionTextParser.parse(Mockito.any())).thenReturn(parsed);
    }

    @Test
    @DisplayName("TX-07: 전부 추출되면 명세 예시 모양으로 200 + missingFields 는 빈 배열")
    void returnsParsedResult() throws Exception {
        parserReturns(new ParsedTransaction(18000, TODAY.minusDays(1), 2L, "저녁 배달 음식"));

        mockMvc.perform(parse("어제 저녁에 배달 음식 18,000원 썼어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(18000))
                .andExpect(jsonPath("$.data.transactionDate").value(TODAY.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.category.id").value(2))
                .andExpect(jsonPath("$.data.category.name").value("배달"))
                .andExpect(jsonPath("$.data.category.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.memo").value("저녁 배달 음식"))
                .andExpect(jsonPath("$.data.missingFields").isArray())
                .andExpect(jsonPath("$.data.missingFields.length()").value(0));
    }

    @Test
    @DisplayName("TX-07: 거래를 저장하지는 않는다 (확인·수정 후 TX-01 이 저장)")
    void doesNotPersistAnything(@Autowired TransactionRepository transactionRepository) throws Exception {
        parserReturns(new ParsedTransaction(18000, TODAY, 2L, "배달"));

        mockMvc.perform(parse("배달 18000원")).andExpect(status().isOk());

        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("TX-07: 오늘(KST)과 DB 카테고리 9개가 프롬프트 입력으로 전달된다")
    void passesTodayAndCategoriesToTheParser() throws Exception {
        parserReturns(new ParsedTransaction(18000, TODAY, 2L, "배달"));

        mockMvc.perform(parse("배달 18000원")).andExpect(status().isOk());

        ArgumentCaptor<TextParseCommand> captor = ArgumentCaptor.forClass(TextParseCommand.class);
        Mockito.verify(transactionTextParser).parse(captor.capture());
        TextParseCommand command = captor.getValue();

        assertThat(command.text()).isEqualTo("배달 18000원");
        assertThat(command.today()).isEqualTo(TODAY);
        assertThat(command.categories()).hasSize(9)
                .extracting(CategoryResponse::name)
                .contains("배달", "월세", "급여");
    }

    @Test
    @DisplayName("TX-07: 일부만 추출되면 그 필드는 null, missingFields 에 이름이 들어간다")
    void partialExtractionIsNotAnError() throws Exception {
        parserReturns(new ParsedTransaction(null, TODAY, 2L, "배달"));

        mockMvc.perform(parse("배달 음식 시켰어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.transactionDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.missingFields").value(Matchers.contains("amount")));
    }

    @Test
    @DisplayName("TX-07: 날짜만 못 뽑혀도 200 — missingFields 는 필수 필드 순서를 지킨다")
    void missingFieldsKeepSpecOrder() throws Exception {
        parserReturns(new ParsedTransaction(null, null, 2L, null));

        mockMvc.perform(parse("배달"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missingFields")
                        .value(Matchers.contains("amount", "transactionDate")));
    }

    @Test
    @DisplayName("TX-07: 모델이 DB 에 없는 카테고리를 고르면 '기타'로 추측하지 않고 추출 실패로 둔다")
    void unknownCategoryBecomesMissing() throws Exception {
        parserReturns(new ParsedTransaction(18000, TODAY, 999L, "배달"));

        mockMvc.perform(parse("배달 18000원"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.missingFields").value(Matchers.contains("category")));
    }

    @Test
    @DisplayName("TX-07: 미래 날짜가 추출돼도 그대로 반환한다 — 거부는 TX-01 의 몫")
    void futureDateIsReturnedAsIs() throws Exception {
        LocalDate tomorrow = TODAY.plusDays(1);
        parserReturns(new ParsedTransaction(18000, tomorrow, 2L, "배달"));

        mockMvc.perform(parse("내일 배달 18000원"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionDate").value(tomorrow.toString()))
                .andExpect(jsonPath("$.data.missingFields.length()").value(0));
    }

    @Test
    @DisplayName("TX-07: 필수 세 필드가 전부 비면 422 AI_PARSE_FAILED")
    void nothingExtractedIsUnprocessable() throws Exception {
        parserReturns(new ParsedTransaction(null, null, null, null));

        mockMvc.perform(parse("ㅁㄴㅇㄹ"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_PARSE_FAILED"));
    }

    @Test
    @DisplayName("TX-07: AI 호출이 실패하면 503 AI_UNAVAILABLE")
    void parserFailureIsServiceUnavailable() throws Exception {
        Mockito.when(transactionTextParser.parse(Mockito.any()))
                .thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

        mockMvc.perform(parse("배달 18000원"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
    }

    @Test
    @DisplayName("TX-07: text 는 필수 1~200자 — 누락·공백·201자는 400")
    void textIsValidated() throws Exception {
        mockMvc.perform(post("/transactions/parse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'text')].reason")
                        .value(Matchers.hasItem("REQUIRED")));

        mockMvc.perform(parse("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(parse("가".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        Mockito.verifyNoInteractions(transactionTextParser);
    }

    @Test
    @DisplayName("TX-07: 토큰 없이 호출하면 401 이고 AI 를 호출하지 않는다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/transactions/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "배달 18000원"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        Mockito.verifyNoInteractions(transactionTextParser);
    }
}

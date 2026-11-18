package com.back.was.domain.youthpolicy;

import com.back.was.domain.youthpolicy.YouthPolicyApiClient.PolicyPage;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 청년정책: 응답 파싱(실제 응답 모양)과 홈 맞춤 목록(YOUTH-01). 외부 API 는 부르지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class YouthPolicyTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired YouthPolicyRepository repository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"홍길동\",\"email\":\"yp@email.com\",\"password\":\"password123!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        token = JsonPath.read(body, "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder r) {
        return r.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void policy(String no, String name, String zips, Integer min, Integer max, String period) {
        YouthPolicyApiClient.Policy p = new YouthPolicyApiClient.Policy(no, name, "청년,주거", "설명입니다", "주거", null,
                "월 20만원 지원", null, period, null, null, "https://apply.example", null, "천안시", min, max, zips);
        YouthPolicy e = YouthPolicy.create(no);
        e.refresh(p, LocalDateTime.now());
        repository.save(e);
    }

    @Test
    @DisplayName("파싱: 실제 응답의 필드를 옮기고, 0 나이는 제한 없음(null), 필수 값 없는 항목은 건너뛴다")
    void parsesResponse() throws Exception {
        PolicyPage page = YouthPolicyApiClient.parse(MAPPER.readTree("""
                {"resultCode":"200","result":{"pagging":{"totCount":135,"pageNum":1,"pageSize":2},
                 "youthPolicyList":[
                  {"plcyNo":"20261111005400114829","plcyNm":"일상돌봄서비스사업","plcyKywdNm":"보조금,상담",
                   "plcyExplnCn":"돌봄\\r\\n서비스","lclsfNm":"금융･복지･문화","aplyYmd":"20260101 ~ 20261201",
                   "sprtTrgtMinAge":"19","sprtTrgtMaxAge":"0","zipCd":"44130,44200","aplyUrlAddr":""},
                  {"plcyNo":"","plcyNm":"번호 없음"}]}}
                """));

        assertThat(page.totalCount()).isEqualTo(135);
        assertThat(page.items()).hasSize(1);
        var p = page.items().get(0);
        assertThat(p.minAge()).isEqualTo(19);
        assertThat(p.maxAge()).isNull();
        assertThat(p.explanation()).doesNotContain("\r");
        assertThat(p.applyUrl()).isNull();
        assertThat(YouthPolicy.parseEnd(p.applyPeriod(), null)).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(YouthPolicy.parseEnd("", null)).isNull();
    }

    @Test
    @DisplayName("YOUTH-01: 내 지역·나이에 맞고 신청 가능한 것만, 지역 맞춤 → 마감 임박 → 상시 순")
    void homeIsPersonalized() throws Exception {
        String future = LocalDate.now().plusDays(10).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String sooner = LocalDate.now().plusDays(3).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String past = LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        policy("N1", "전국형 상시", "44130,44200,11110,11140,11170,11200", 19, 34, "");
        policy("L1", "천안 마감 늦음", "44130", 19, 39, "20260101 ~ " + future);
        policy("L2", "천안 마감 임박", "44130", 19, 39, "20260101 ~ " + sooner);
        policy("X1", "아산 전용", "44200", 19, 39, "");                       // 다른 지역
        policy("A1", "고령 대상", "44130", 60, 80, "");                       // 나이 안 맞음
        policy("E1", "마감 지남", "44130", 19, 39, "20250101 ~ " + past);     // 마감

        mockMvc.perform(authed(put("/users/me/onboarding").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionCode\":\"44130\",\"age\":27,\"gender\":\"NONE\"}")))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/youth-policies/home")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("천안 마감 임박"))
                .andExpect(jsonPath("$.data[0].dDay").value(3))
                .andExpect(jsonPath("$.data[0].regionSpecific").value(true))
                .andExpect(jsonPath("$.data[1].name").value("천안 마감 늦음"))
                .andExpect(jsonPath("$.data[2].name").value("전국형 상시"))
                .andExpect(jsonPath("$.data[2].dDay").doesNotExist())
                .andExpect(jsonPath("$.data[2].ageText").value("만 19~34세"))
                .andExpect(jsonPath("$.data[2].applyPeriod").value("상시"));
    }

    @Test
    @DisplayName("YOUTH-01: limit 이 적용되고, 지역을 안 정했으면 400, 토큰이 없으면 401")
    void limitAndPreconditions() throws Exception {
        mockMvc.perform(authed(get("/youth-policies/home"))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/youth-policies/home")).andExpect(status().isUnauthorized());

        policy("L1", "가", "44130", null, null, "");
        policy("L2", "나", "44130", null, null, "");
        mockMvc.perform(authed(put("/users/me/onboarding").contentType(MediaType.APPLICATION_JSON)
                .content("{\"regionCode\":\"44130\",\"age\":27,\"gender\":\"NONE\"}"))).andExpect(status().isOk());
        mockMvc.perform(authed(get("/youth-policies/home").param("limit", "1")))
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}

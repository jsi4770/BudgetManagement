package com.back.was.domain.merchant;

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
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 지역 설정(USER-02) → 지역 가맹점 목록(MERCHANT-01·02). 공공 API 는 부르지 않고 테이블에 직접 넣는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocalMerchantApiTest {

    private static final String CHEONAN = "44130";
    private static final String ASAN = "44200";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LocalMerchantRepository merchantRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "merchant@email.com", "password": "password123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        accessToken = JsonPath.read(body, "$.data.accessToken");

        save("a1", CHEONAN, "네네치킨성정점", MerchantGroup.FOOD);
        save("a2", CHEONAN, "스타벅스 천안점", MerchantGroup.CAFE);
        save("a3", CHEONAN, "행복마트", MerchantGroup.GROCERY);
        save("b1", ASAN, "아산온천식당", MerchantGroup.FOOD);
    }

    private void save(String key, String region, String name, MerchantGroup group) {
        LocalMerchant m = LocalMerchant.create(key, region);
        m.refresh(name, "성정동", "충남 천안시 어딘가 1", "0411234567", 36.8, 127.1, "561", "음식점 및 주점업",
                group, LocalDate.of(2023, 11, 17), LocalDateTime.now());
        merchantRepository.save(m);
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private void setRegion(String code) throws Exception {
        mockMvc.perform(authed(patch("/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionCode\": \"%s\"}".formatted(code))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER-02: 지원 지역을 저장하면 USER-01 이 regionCode·regionName 을 돌려준다")
    void regionIsSavedOnProfile() throws Exception {
        setRegion(ASAN);

        mockMvc.perform(authed(get("/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionCode").value(ASAN))
                .andExpect(jsonPath("$.data.regionName").value("아산시"));
    }

    @Test
    @DisplayName("USER-02: 지원하지 않는 지역 코드는 400 VALIDATION_ERROR")
    void unsupportedRegionIsRejected() throws Exception {
        mockMvc.perform(authed(patch("/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionCode\": \"11110\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("MERCHANT-01: 지원 지역은 천안·아산 두 곳이다")
    void regionsList() throws Exception {
        mockMvc.perform(authed(get("/local-merchants/regions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value(CHEONAN))
                .andExpect(jsonPath("$.data[0].name").value("천안시"));
    }

    @Test
    @DisplayName("MERCHANT-02: 내 지역의 가맹점만 상호순으로 나온다")
    void listsOnlyMyRegion() throws Exception {
        setRegion(CHEONAN);

        mockMvc.perform(authed(get("/local-merchants")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items[0].name").value("네네치킨성정점"))
                .andExpect(jsonPath("$.data.items[0].groupLabel").value("식당·주점"));
    }

    @Test
    @DisplayName("MERCHANT-02: group 과 q 로 거를 수 있다")
    void filtersByGroupAndKeyword() throws Exception {
        setRegion(CHEONAN);

        mockMvc.perform(authed(get("/local-merchants").param("group", "CAFE")))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("스타벅스 천안점"));

        mockMvc.perform(authed(get("/local-merchants").param("q", "마트")))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].group").value("GROCERY"));
    }

    @Test
    @DisplayName("MERCHANT-02: regionCode 파라미터는 내 설정보다 우선한다 — 지역을 안 정해도 둘러볼 수 있다")
    void regionParamOverridesProfile() throws Exception {
        mockMvc.perform(authed(get("/local-merchants").param("regionCode", ASAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("아산온천식당"));
    }

    @Test
    @DisplayName("MERCHANT-02: 지역도 파라미터도 없으면 400 VALIDATION_ERROR")
    void noRegionIsRejected() throws Exception {
        mockMvc.perform(authed(get("/local-merchants")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("MERCHANT-02: 지원하지 않는 regionCode 는 400, 없는 group 값도 400")
    void invalidParamsAreRejected() throws Exception {
        mockMvc.perform(authed(get("/local-merchants").param("regionCode", "11110")))
                .andExpect(status().isBadRequest());
        setRegion(CHEONAN);
        mockMvc.perform(authed(get("/local-merchants").param("group", "NOPE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MERCHANT-02: size 는 50 을 넘기지 못하고, 토큰이 없으면 401")
    void sizeIsCappedAndAuthRequired() throws Exception {
        setRegion(CHEONAN);
        mockMvc.perform(authed(get("/local-merchants").param("size", "500")))
                .andExpect(jsonPath("$.data.size").value(50));

        mockMvc.perform(get("/local-merchants"))
                .andExpect(status().isUnauthorized());
    }
}

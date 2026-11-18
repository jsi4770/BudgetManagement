package com.back.was.domain.user;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** USER-03 온보딩(지역·나이·성별)과 REGION-01 충남 전체 목록. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OnboardingApiTest {

    @Autowired
    MockMvc mockMvc;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"홍길동\",\"email\":\"onb@email.com\",\"password\":\"password123!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        token = JsonPath.read(body, "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder r) {
        return r.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private org.springframework.test.web.servlet.ResultActions onboard(String json) throws Exception {
        return mockMvc.perform(authed(put("/users/me/onboarding").contentType(MediaType.APPLICATION_JSON).content(json)));
    }

    @Test
    @DisplayName("가입 직후에는 onboardingCompleted=false, 온보딩 후에는 true 이고 나이·성별이 보인다")
    void onboardingFlow() throws Exception {
        mockMvc.perform(authed(get("/users/me")))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.age").doesNotExist());

        onboard("{\"regionCode\":\"44130\",\"age\":27,\"gender\":\"FEMALE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.regionName").value("천안시"))
                .andExpect(jsonPath("$.data.age").value(27))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));

        mockMvc.perform(authed(get("/users/me")))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    @DisplayName("성별 '선택 안 함'(NONE)도 온보딩을 완료로 본다")
    void noneGenderIsAccepted() throws Exception {
        onboard("{\"regionCode\":\"44200\",\"age\":24,\"gender\":\"NONE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gender").value("NONE"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    @DisplayName("선택 불가 지역·범위 밖 나이·값 누락·없는 성별은 400")
    void invalidInputs() throws Exception {
        onboard("{\"regionCode\":\"44150\",\"age\":27,\"gender\":\"NONE\"}").andExpect(status().isBadRequest());   // 공주(readonly)
        onboard("{\"regionCode\":\"44130\",\"age\":5,\"gender\":\"NONE\"}").andExpect(status().isBadRequest());
        onboard("{\"regionCode\":\"44130\",\"age\":27}").andExpect(status().isBadRequest());                       // 성별 누락
        onboard("{\"regionCode\":\"44130\",\"age\":27,\"gender\":\"X\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("REGION-01: 충남 15개 시·군, 천안·아산만 selectable 이고 앞에 온다")
    void chungnamRegions() throws Exception {
        mockMvc.perform(authed(get("/regions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(15))
                .andExpect(jsonPath("$.data[0].name").value("아산시"))
                .andExpect(jsonPath("$.data[0].selectable").value(true))
                .andExpect(jsonPath("$.data[1].name").value("천안시"))
                .andExpect(jsonPath("$.data[2].selectable").value(false))
                .andExpect(jsonPath("$.data[?(@.selectable==true)].name",
                        org.hamcrest.Matchers.containsInAnyOrder("천안시", "아산시")));
    }

    @Test
    @DisplayName("나이는 출생연도로 저장돼 올해 기준으로 계산된다")
    void ageRoundTrips() throws Exception {
        onboard("{\"regionCode\":\"44130\",\"age\":30,\"gender\":\"MALE\"}").andExpect(status().isOk());
        mockMvc.perform(authed(get("/users/me"))).andExpect(jsonPath("$.data.age").value(30));
        org.assertj.core.api.Assertions.assertThat(LocalDate.now().getYear()).isGreaterThan(2000);
    }
}

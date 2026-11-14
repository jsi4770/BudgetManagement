package com.back.was.domain.ai;

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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 코칭 흐름의 실제 빈 연결(목 없이): Gemini mock 모드에서는 템플릿이 아니라 <b>Gemini 구현이</b> 쓰이고,
 * 정상 응답이면 {@code isFallback} 이 {@code false} 다. (키 없음 → {@code true} 는 기존
 * {@code CoachingMessageApiTest} 가 본다.)
 */
@SpringBootTest(properties = {"gemini.mock=true", "ocr.base-url="})
@AutoConfigureMockMvc
@Transactional
class AiCoachingMockModeApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CoachingOpeningGenerator openingGenerator;
    @Autowired
    CoachingReplyGenerator replyGenerator;
    @Autowired
    NudgeTextGenerator nudgeTextGenerator;
    @Autowired
    GrowthFeedbackGenerator growthFeedbackGenerator;

    private String token;

    @BeforeEach
    void signUp() throws Exception {
        token = AiWiringSupport.signUp(mockMvc, "ai-coaching-mock@email.com");
    }

    @Test
    @DisplayName("네 포트가 모두 Gemini 구현으로 연결된다 (템플릿은 실패 시 위임용으로만 남는다)")
    void portsAreWiredToGemini() {
        assertThat(openingGenerator).isInstanceOf(GeminiCoachingOpeningGenerator.class);
        assertThat(replyGenerator).isInstanceOf(GeminiCoachingReplyGenerator.class);
        assertThat(nudgeTextGenerator).isInstanceOf(GeminiNudgeTextGenerator.class);
        assertThat(growthFeedbackGenerator).isInstanceOf(GeminiGrowthFeedbackGenerator.class);
    }

    @Test
    @DisplayName("COACH-01·03: Gemini 응답이 그대로 내려가고 isFallback 은 false")
    void coachingUsesGemini() throws Exception {
        String started = mockMvc.perform(post("/coachings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                // mock 은 문자열 필드를 "mock" 으로 채운다 — 템플릿 문구("님, ...")가 아니라 Gemini 경로라는 증거
                .andExpect(jsonPath("$.data.messages[0].content").value("mock"))
                .andReturn().getResponse().getContentAsString();
        Number coachingId = JsonPath.read(started, "$.data.coachingId");

        mockMvc.perform(post("/coachings/{id}/messages", coachingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"배달을 줄이고 싶어요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiMessage.content").value("mock"))
                .andExpect(jsonPath("$.data.aiMessage.isFallback").value(false));
    }
}

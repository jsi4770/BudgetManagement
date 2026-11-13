package com.back.was.domain.ai;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AI 배선 통합 테스트 공용 — 가입·토큰 발급과 두 엔드포인트 호출. */
final class AiWiringSupport {

    private AiWiringSupport() {
    }

    static String signUp(MockMvc mockMvc, String email) throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "password123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    static ResultActions parse(MockMvc mockMvc, String token, String text) throws Exception {
        return mockMvc.perform(post("/transactions/parse")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"text": "%s"}
                        """.formatted(text)));
    }

    static ResultActions receipt(MockMvc mockMvc, String token) throws Exception {
        return mockMvc.perform(multipart("/receipts/ocr")
                .file(new MockMultipartFile("image", "receipt.jpg", "image/jpeg", "fake-jpeg".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}

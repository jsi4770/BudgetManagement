package com.back.was.domain.ai;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** {@link AiWiringSupport}(패키지 전용)를 다른 패키지의 테스트에서 쓰기 위한 공개 래퍼. */
public final class AiWiringSupportAccess {

    private AiWiringSupportAccess() {
    }

    public static String signUp(MockMvc mockMvc, String email) throws Exception {
        return AiWiringSupport.signUp(mockMvc, email);
    }

    public static ResultActions receipt(MockMvc mockMvc, String token) throws Exception {
        return AiWiringSupport.receipt(mockMvc, token);
    }
}

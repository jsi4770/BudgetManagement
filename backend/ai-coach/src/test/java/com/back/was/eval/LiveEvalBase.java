package com.back.was.eval;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** 실제 Gemini 를 부르는 평가. GEMINI_API_KEY 환경변수가 있을 때만 실행된다 (없으면 건너뜀). EVAL_N 으로 반복 횟수 조절 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
abstract class LiveEvalBase {
    static int n() {
        try { return Integer.parseInt(System.getenv().getOrDefault("EVAL_N", "1")); } catch (NumberFormatException e) { return 1; }
    }
}

package com.back.was.ai.gemini;

import com.back.was.common.ApiResponses;
import tools.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 연결 확인용. local 프로필에서만 열린다 (배포 서버에는 안 생김).
 *   GET /dev/ai/ping?text=배민 3.4 → {"success":true,"data":{"amount":34000,"memo":"배민"}}
 * ※ 다른 컨트롤러가 /api/v1/... 로 시작하면 경로를 맞추고, Security 에서 local 일 때 permitAll.
 */
@Profile("local")
@RestController
@RequestMapping("/dev/ai")
public class DevGeminiController {

    private final GeminiClient gemini;

    public DevGeminiController(GeminiClient gemini) {
        this.gemini = gemini;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping(@RequestParam(defaultValue = "배민 3.4") String text) {
        Map<String, Map<String, Object>> props = new LinkedHashMap<>();
        props.put("amount", GeminiSchema.nullable(GeminiSchema.integer("원 단위 정수. 없으면 null")));
        props.put("memo", GeminiSchema.nullable(GeminiSchema.string("가게명·품목")));

        JsonNode data = gemini.generateJson(
                "너는 가계부 입력 해석기다. 금액(원)과 메모만 뽑는다. "
                        + "단위 없는 작은 수는 항목의 통상 가격대로 천원/만원 단위를 판단한다. 예: 배민 3.4 = 34000",
                text, GeminiSchema.object(props), 0.0);
        return ApiResponses.ok(data);
    }
}

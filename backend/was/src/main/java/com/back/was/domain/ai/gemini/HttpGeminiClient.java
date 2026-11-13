package com.back.was.domain.ai.gemini;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.back.was.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실제 Gemini REST 호출. POST {baseUrl}/models/{model}:generateContent
 * gemini.mock=false(기본)일 때만 빈으로 등록된다.
 */
@Component
@ConditionalOnProperty(prefix = "gemini", name = "mock", havingValue = "false", matchIfMissing = true)
public class HttpGeminiClient implements GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpGeminiClient.class);

    private final RestClient restClient;
    private final GeminiProperties props;
    private final ObjectMapper objectMapper;

    public HttpGeminiClient(@Qualifier("geminiRestClient") RestClient restClient,
                            GeminiProperties props, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.props = props;
        this.objectMapper = objectMapper;

        // 설정 상태만 로그로. 키 값 자체는 절대 찍지 않는다.
        if (props.hasApiKey()) {
            log.info("[Gemini] 연결 준비 완료 - model={}", props.model());
        } else {
            // 키가 없어도 서버는 뜨게 둔다 (AI 안 쓰는 팀원 작업 방해 X). 호출 시점에 503.
            log.warn("[Gemini] GEMINI_API_KEY 환경변수가 없습니다. AI 기능 호출 시 503(AI_UNAVAILABLE)이 반환됩니다.");
        }
    }

    @Override
    public JsonNode generateJson(String systemInstruction, List<GeminiMessage> messages,
                                 Map<String, Object> responseSchema, double temperature) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (GeminiMessage m : messages) {
            contents.add(Map.of("role", m.role(), "parts", List.of(Map.of("text", m.text()))));
        }
        return call(systemInstruction, contents, responseSchema, temperature);
    }

    @Override
    public JsonNode generateJson(String systemInstruction, String userText,
                                 byte[] imageBytes, String mimeType,
                                 Map<String, Object> responseSchema, double temperature) {
        // 이미지는 base64 로 본문에 직접 넣는다 (inlineData). 10MB 이하 영수증이면 충분
        List<Map<String, Object>> parts = List.of(
                Map.of("inlineData", Map.of(
                        "mimeType", mimeType,
                        "data", Base64.getEncoder().encodeToString(imageBytes))),
                Map.of("text", userText));
        List<Map<String, Object>> contents = List.of(Map.of("role", "user", "parts", parts));
        return call(systemInstruction, contents, responseSchema, temperature);
    }

    // ------------------------------------------------------------------ 내부

    private JsonNode call(String systemInstruction, List<Map<String, Object>> contents,
                          Map<String, Object> responseSchema, double temperature) {
        if (!props.hasApiKey()) {
            throw GeminiErrors.unavailable("AI 서비스 설정이 되어 있지 않아요.");
        }

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");   // JSON 으로만 답하게
        generationConfig.put("responseSchema", responseSchema);         // 이 모양으로만
        generationConfig.put("temperature", temperature);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        body.put("contents", contents);
        body.put("generationConfig", generationConfig);

        return extractJson(post(body));
    }

    /**
     * 모델별로 재시도하고, 계속 실패하면 예비 모델로 전환한다.
     * 429(일일 한도 초과)는 재시도해도 소용없으므로 곧바로 다음 모델로 넘어간다.
     */
    private JsonNode post(Map<String, Object> body) {
        List<String> models = new ArrayList<>();
        models.add(props.model());
        if (props.fallbackModel() != null) models.add(props.fallbackModel());

        BusinessException last = null;
        for (String model : models) {
            int attempts = props.maxRetries() + 1;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    return restClient.post()
                            .uri("/models/{model}:generateContent", model)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class);
                } catch (RestClientResponseException e) {
                    int status = e.getStatusCode().value();
                    logHttpError(status, e.getResponseBodyAsString());
                    last = GeminiErrors.unavailable("AI 서비스를 일시적으로 사용할 수 없어요.", e);
                    if (status == 429) break;
                    if (status >= 500 && attempt < attempts) { sleep(1500L * attempt); continue; }
                    if (status >= 500) break;
                    throw last;   // 400·403·404 는 설정 문제 → 예비 모델도 소용없음
                } catch (ResourceAccessException e) {
                    log.warn("[Gemini] {} 네트워크 오류/타임아웃 (시도 {}/{}): {}", model, attempt, attempts, e.getMessage());
                    last = GeminiErrors.unavailable("AI 서비스에 연결하지 못했어요.", e);
                    if (attempt < attempts) sleep(1500L * attempt);
                }
            }
            log.warn("[Gemini] {} 응답 불가 → 예비 모델 확인", model);
        }
        throw last != null ? last : GeminiErrors.unavailable("AI 서비스를 일시적으로 사용할 수 없어요.");
    }

    // ------------------------------------------------------------------ 도구 호출 대화

    private static final int MAX_TOOL_ROUNDS = 5;

    @Override
    public String chatWithTools(String systemInstruction, List<GeminiMessage> messages,
                                List<GeminiTool> tools, double temperature) {
        if (!props.hasApiKey()) {
            throw GeminiErrors.unavailable("AI 서비스 설정이 되어 있지 않아요.");
        }
        Map<String, GeminiTool> byName = new LinkedHashMap<>();
        List<Map<String, Object>> decls = new ArrayList<>();
        for (GeminiTool t : tools) {
            byName.put(t.name(), t);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", t.name());
            d.put("description", t.description());
            if (t.parameters() != null) d.put("parameters", t.parameters());
            decls.add(d);
        }
        List<Map<String, Object>> contents = new ArrayList<>();
        for (GeminiMessage m : messages) {
            contents.add(Map.of("role", m.role(), "parts", List.of(Map.of("text", m.text()))));
        }

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
            body.put("contents", contents);
            body.put("tools", List.of(Map.of("functionDeclarations", decls)));
            body.put("generationConfig", Map.of("temperature", temperature));

            JsonNode candidate = post(body).path("candidates").path(0);
            JsonNode parts = candidate.path("content").path("parts");

            List<Map<String, Object>> responses = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.has("functionCall")) {
                    JsonNode call = part.get("functionCall");
                    GeminiTool tool = byName.get(call.path("name").asString(""));
                    Object result;
                    try {
                        result = tool == null ? Map.of("error", "없는 도구") : tool.handler().apply(call.path("args"));
                    } catch (RuntimeException e) {
                        result = Map.of("error", String.valueOf(e.getMessage()));
                    }
                    responses.add(Map.of("functionResponse", Map.of(
                            "name", call.path("name").asString(""),
                            "response", Map.of("result", result))));
                } else if (part.has("text")) {
                    text.append(part.path("text").asString(""));
                }
            }
            if (responses.isEmpty()) {
                if (text.length() == 0) throw GeminiErrors.parseFailed("AI 응답이 비어 있어요.");
                return text.toString().trim();
            }
            contents.add(objectMapper.convertValue(candidate.path("content"), Map.class));  // 모델의 functionCall 턴
            contents.add(Map.of("role", "user", "parts", responses));
        }
        throw GeminiErrors.parseFailed("도구 호출이 끝나지 않았어요.");
    }

    /** 응답 구조: candidates[0].content.parts[*].text = "{...}" → JSON 으로 파싱 */
    private JsonNode extractJson(JsonNode response) {
        JsonNode candidate = response == null ? null : response.path("candidates").path(0);
        if (candidate == null || candidate.isMissingNode()) {
            // 안전 필터에 걸리면 candidates 없이 promptFeedback.blockReason 만 온다
            log.warn("[Gemini] 응답 후보 없음: {}", response);
            throw GeminiErrors.parseFailed("입력에서 정보를 추출하지 못했어요.");
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            text.append(part.path("text").asString(""));
        }
        String raw = text.toString().trim();
        if (raw.isEmpty()) {
            log.warn("[Gemini] 빈 응답. finishReason={}", candidate.path("finishReason").asString());
            throw GeminiErrors.parseFailed("입력에서 정보를 추출하지 못했어요.");
        }

        // 혹시 ```json ... ``` 으로 감싸 오면 벗겨낸다 (스키마 강제 시 보통 없음)
        raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");

        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("[Gemini] JSON 파싱 실패: {}", raw);
            throw GeminiErrors.parseFailed("입력에서 정보를 추출하지 못했어요.", e);
        }
    }

    /** 원인 파악용 로그. 상태 코드별 흔한 원인을 같이 남긴다 */
    private void logHttpError(int status, String body) {
        String hint = switch (status) {
            case 400 -> "요청 형식 오류 또는 API 키 오타(API key not valid)";
            case 403 -> "키 권한 없음/차단 (유출된 키면 새로 발급)";
            case 404 -> "모델명 오류 → GEMINI_MODEL 확인";
            case 429 -> "무료 한도 초과 → 잠시 후 재시도";
            default -> status >= 500 ? "Gemini 서버 장애" : "기타";
        };
        String shortBody = body == null ? "" : body.substring(0, Math.min(body.length(), 300));
        log.warn("[Gemini] HTTP {} ({}) {}", status, hint, shortBody);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

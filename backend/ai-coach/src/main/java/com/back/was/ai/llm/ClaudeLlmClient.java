package com.back.was.ai.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.Timeout;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.back.was.common.AiParseFailedException;
import com.back.was.common.AiUnavailableException;
import com.back.was.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 실제 Claude 호출 (Messages API, 공식 Java SDK). claude.mock=false(기본)일 때만 빈으로 등록된다.
 *
 * <p>JSON 은 구조화 출력({@code output_config.format = json_schema})으로 받는다. 사고(thinking)는 모델 기본값
 * (adaptive)에 맡기고 깊이만 {@code claude.effort} 로 정한다.
 *
 * <p><b>시간 상한은 시도마다가 아니라 호출 전체에 건다.</b> 기본·예비 모델 × (재시도 + 1) 번을 각자 기다리면
 * 최악 수십 초가 된다. 그래서 SDK 의 자체 재시도는 끄고(maxRetries 0) 이 클래스가 재시도·예비 모델 전환을
 * 하며, 시도마다 <b>남은 시간</b>만큼만 요청 타임아웃을 준다. 도구 호출 대화는 왕복 전체가 상한 하나 안에 든다.
 */
@Component
@ConditionalOnProperty(prefix = "claude", name = "mock", havingValue = "false", matchIfMissing = true)
public class ClaudeLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_TOOL_ROUNDS = 5;

    /** 키가 없으면 null — 만들지 않고, 호출 시점에 503 을 낸다. */
    private final AnthropicClient client;
    private final ClaudeProperties props;
    private final ObjectMapper objectMapper;
    private final OutputConfig.Effort effort;

    public ClaudeLlmClient(ClaudeProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.effort = OutputConfig.Effort.of(props.effort());

        // 설정 상태만 로그로. 키 값 자체는 절대 찍지 않는다.
        if (props.hasApiKey()) {
            AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                    .apiKey(props.apiKey())
                    .maxRetries(0)   // 재시도는 이 클래스가 마감 안에서만 한다
                    .timeout(Timeout.builder().connect(CONNECT_TIMEOUT).request(props.imageTimeout()).build());
            if (props.baseUrl() != null) builder.baseUrl(props.baseUrl());
            this.client = builder.build();
            log.info("[Claude] 연결 준비 완료 - model={}, effort={}", props.model(), props.effort());
        } else {
            this.client = null;
            // 키가 없어도 서버는 뜨게 둔다 (AI 안 쓰는 팀원 작업 방해 X). 호출 시점에 503.
            log.warn("[Claude] ANTHROPIC_API_KEY 환경변수가 없습니다. AI 기능 호출 시 503(AI_UNAVAILABLE)이 반환됩니다.");
        }
    }

    @Override
    public JsonNode generateJson(String systemInstruction, List<LlmMessage> messages,
                                 Map<String, Object> responseSchema) {
        return callJson(props.timeout(), systemInstruction, responseSchema, b -> addMessages(b, messages));
    }

    @Override
    public JsonNode generateJson(String systemInstruction, String userText,
                                 byte[] imageBytes, String mimeType,
                                 Map<String, Object> responseSchema) {
        // 이미지는 base64 로 본문에 직접 넣는다. 크기는 ReceiptImageScaler 가 이미 줄였다(Claude 한도 5MB)
        Base64ImageSource image = Base64ImageSource.builder()
                .mediaType(Base64ImageSource.MediaType.of(normalizeMime(mimeType)))
                .data(Base64.getEncoder().encodeToString(imageBytes))
                .build();
        return callJson(props.imageTimeout(), systemInstruction, responseSchema, b ->
                b.addUserMessageOfContentBlockParams(List.of(
                        ContentBlockParam.ofImage(image),      // 이미지를 텍스트보다 앞에
                        ContentBlockParam.ofText(userText))));
    }

    // ------------------------------------------------------------------ 도구 호출 대화

    @Override
    public String chatWithTools(String systemInstruction, List<LlmMessage> messages, List<LlmTool> tools) {
        requireClient();
        Map<String, LlmTool> byName = new LinkedHashMap<>();
        MessageCreateParams.Builder conversation = MessageCreateParams.builder()
                .maxTokens(props.maxTokens())
                .system(systemInstruction)
                .outputConfig(OutputConfig.builder().effort(effort).build());
        for (LlmTool t : tools) {
            byName.put(t.name(), t);
            conversation.addTool(toTool(t));
        }
        addMessages(conversation, messages);

        // 왕복 전체에 상한 하나 — 라운드마다 새로 주면 최악이 라운드 수만큼 곱해진다
        Instant deadline = Instant.now().plus(props.timeout());
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Message response = post(conversation, deadline);
            checkStop(response);

            List<ContentBlockParam> results = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (ContentBlock block : response.content()) {
                block.toolUse().ifPresent(use -> results.add(runTool(use, byName)));
                block.text().ifPresent(t -> text.append(t.text()));
            }
            if (results.isEmpty()) {
                if (text.length() == 0) throw new AiParseFailedException("AI 응답이 비어 있어요.");
                return text.toString().trim();
            }
            // 모델의 턴(사고·도구 호출 블록 포함)을 그대로 붙이고, 결과는 user 턴 하나에 모아 보낸다
            conversation.addMessage(response.toParam());
            conversation.addUserMessageOfContentBlockParams(results);
        }
        throw new AiParseFailedException("도구 호출이 끝나지 않았어요.");
    }

    /** 실패해도 대화를 끊지 않는다 — 오류를 is_error 결과로 돌려 모델이 고쳐 부르게 한다. */
    private ContentBlockParam runTool(ToolUseBlock use, Map<String, LlmTool> byName) {
        LlmTool tool = byName.get(use.name());
        String content;
        boolean error = false;
        try {
            if (tool == null) {
                content = "없는 도구: " + use.name();
                error = true;
            } else {
                Object input = use._input().convert(Object.class);
                JsonNode args = objectMapper.valueToTree(input == null ? Map.of() : input);
                content = objectMapper.writeValueAsString(tool.handler().apply(args));
            }
        } catch (RuntimeException e) {
            content = String.valueOf(e.getMessage());
            error = true;
        }
        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(use.id())
                .content(content)
                .isError(error)
                .build());
    }

    private static Tool toTool(LlmTool t) {
        Map<String, Object> schema = t.parameters() != null ? t.parameters() : LlmSchema.object(Map.of());
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        Object props = schema.getOrDefault("properties", Map.of());
        ((Map<?, ?>) props).forEach((k, v) -> properties.putAdditionalProperty(String.valueOf(k), JsonValue.from(v)));

        Tool.InputSchema.Builder input = Tool.InputSchema.builder().properties(properties.build());
        if (schema.get("required") instanceof List<?> required) {
            input.required(required.stream().map(String::valueOf).toList());
        }
        return Tool.builder().name(t.name()).description(t.description()).inputSchema(input.build()).build();
    }

    // ------------------------------------------------------------------ 내부

    private void requireClient() {
        if (client == null) {
            throw new AiUnavailableException("AI 서비스 설정이 되어 있지 않아요.");
        }
    }

    private static void addMessages(MessageCreateParams.Builder b, List<LlmMessage> messages) {
        for (LlmMessage m : messages) {
            if (LlmMessage.ASSISTANT.equals(m.role())) {
                b.addAssistantMessage(m.text());
            } else {
                b.addUserMessage(m.text());
            }
        }
    }

    /** {@code image/jpg} 는 비표준이라 API 가 받지 않는다. */
    private static String normalizeMime(String mimeType) {
        return "image/jpg".equalsIgnoreCase(mimeType) ? "image/jpeg" : mimeType.toLowerCase();
    }

    private JsonNode callJson(Duration limit, String systemInstruction, Map<String, Object> responseSchema,
                              Consumer<MessageCreateParams.Builder> messages) {
        requireClient();

        MessageCreateParams.Builder base = MessageCreateParams.builder()
                .maxTokens(props.maxTokens())
                .system(systemInstruction)
                .outputConfig(OutputConfig.builder()
                        .effort(effort)
                        .format(JsonOutputFormat.builder()
                                .schema(schema(responseSchema))
                                .build())
                        .build());
        messages.accept(base);

        return extractJson(post(base, Instant.now().plus(limit)));
    }

    private static JsonOutputFormat.Schema schema(Map<String, Object> responseSchema) {
        JsonOutputFormat.Schema.Builder b = JsonOutputFormat.Schema.builder();
        responseSchema.forEach((k, v) -> b.putAdditionalProperty(k, JsonValue.from(v)));
        return b.build();
    }

    /**
     * 모델별로 재시도하고, 계속 실패하면 예비 모델로 전환한다.
     * 429(한도 초과)는 재시도해도 소용없으므로 곧바로 다음 모델로 넘어간다.
     *
     * <p><b>전부 {@code deadline} 안에서만 일어난다.</b> 마감이 지나면 남은 재시도와 예비 모델을 건너뛰고 마지막
     * 실패로 503 을 낸다 — 시도 횟수가 아니라 시간이 사용자 대기의 상한이다.
     */
    private Message post(MessageCreateParams.Builder base, Instant deadline) {
        List<String> models = new ArrayList<>();
        models.add(props.model());
        if (props.fallbackModel() != null) models.add(props.fallbackModel());

        BusinessException last = null;
        models:
        for (String model : models) {
            int attempts = props.maxRetries() + 1;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                Duration remaining = Duration.between(Instant.now(), deadline);
                if (remaining.isNegative() || remaining.isZero()) {
                    log.warn("[Claude] 호출 시간 상한 초과 — 남은 재시도·예비 모델을 건너뛴다");
                    break models;
                }
                try {
                    return client.messages().create(base.model(model).build(), within(remaining));
                } catch (AnthropicServiceException e) {
                    int status = e.statusCode();
                    logHttpError(model, status);
                    last = new AiUnavailableException("AI 서비스를 일시적으로 사용할 수 없어요.", e);
                    if (status == 429) break;
                    if (status >= 500 && attempt < attempts && backOff(attempt, deadline)) continue;
                    if (status >= 500) break;
                    throw last;   // 400·401·403·404 는 설정 문제 → 예비 모델도 소용없음
                } catch (AnthropicIoException e) {
                    // 연결 실패·타임아웃
                    log.warn("[Claude] {} 네트워크 오류/타임아웃 (시도 {}/{}): {}", model, attempt, attempts, e.getClass().getSimpleName());
                    last = new AiUnavailableException("AI 서비스에 연결하지 못했어요.", e);
                    if (attempt < attempts && !backOff(attempt, deadline)) break;
                } catch (AnthropicException e) {
                    // 응답 본문을 읽을 수 없음 등. 안 잡으면 500 이 된다.
                    log.warn("[Claude] {} 응답 처리 실패: {}", model, e.getClass().getSimpleName());
                    last = new AiUnavailableException("AI 서비스를 일시적으로 사용할 수 없어요.", e);
                    break;
                }
            }
            log.warn("[Claude] {} 응답 불가 → 예비 모델 확인", model);
        }
        throw last != null ? last : new AiUnavailableException("AI 서비스를 일시적으로 사용할 수 없어요.");
    }

    /** 시도 하나의 타임아웃을 마감까지 남은 시간으로 줄인다 — 마감 직전에 시작한 시도가 상한을 넘기지 않게. */
    private static RequestOptions within(Duration remaining) {
        Duration connect = remaining.compareTo(CONNECT_TIMEOUT) < 0 ? remaining : CONNECT_TIMEOUT;
        return RequestOptions.builder()
                .timeout(Timeout.builder().connect(connect).read(remaining).request(remaining).build())
                .build();
    }

    /** 재시도 전 대기. 대기만으로 마감을 넘기면 기다리지 않고 {@code false} — 재시도할 시간이 없다. */
    private static boolean backOff(int attempt, Instant deadline) {
        long waitMillis = 1500L * attempt;
        if (!Instant.now().plusMillis(waitMillis).isBefore(deadline)) {
            return false;
        }
        sleep(waitMillis);
        return true;
    }

    /** 모델이 거절(refusal)했거나 토큰 상한에서 잘렸으면 읽을 수 없는 응답이다 → 422. */
    private void checkStop(Message response) {
        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            log.warn("[Claude] 모델이 응답을 거절했다: {}", response.stopDetails()
                    .flatMap(d -> d.category()).map(Object::toString).orElse("-"));
            throw new AiParseFailedException("입력에서 정보를 추출하지 못했어요.");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            log.warn("[Claude] 토큰 상한(claude.max-tokens={})에서 응답이 잘렸다", props.maxTokens());
            throw new AiParseFailedException("입력에서 정보를 추출하지 못했어요.");
        }
    }

    /** 텍스트 블록을 이어 붙여 JSON 으로 파싱한다. 사고(thinking) 블록은 건너뛴다. */
    private JsonNode extractJson(Message response) {
        checkStop(response);

        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
        }
        String raw = text.toString().trim();
        if (raw.isEmpty()) {
            log.warn("[Claude] 빈 응답. stopReason={}", response.stopReason().orElse(null));
            throw new AiParseFailedException("입력에서 정보를 추출하지 못했어요.");
        }

        // 혹시 ```json ... ``` 으로 감싸 오면 벗겨낸다 (구조화 출력이면 보통 없음)
        raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");

        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            // 원문은 남기지 않는다 — 사용자 입력이 섞여 있을 수 있다
            log.warn("[Claude] JSON 파싱 실패 (길이 {})", raw.length());
            throw new AiParseFailedException("입력에서 정보를 추출하지 못했어요.", e);
        }
    }

    /** 원인 파악용 로그. 상태 코드별 흔한 원인을 같이 남긴다 */
    private static void logHttpError(String model, int status) {
        String hint = switch (status) {
            case 400 -> "요청 형식 오류 (스키마·파라미터 확인)";
            case 401 -> "API 키 오류 → ANTHROPIC_API_KEY 확인";
            case 403 -> "키 권한 없음";
            case 404 -> "모델명 오류 → CLAUDE_MODEL 확인";
            case 413 -> "요청이 너무 큼 (이미지 크기 확인)";
            case 429 -> "한도 초과 → 예비 모델 또는 잠시 후 재시도";
            case 529 -> "Claude 과부하";
            default -> status >= 500 ? "Claude 서버 장애" : "기타";
        };
        log.warn("[Claude] {} HTTP {} ({})", model, status, hint);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

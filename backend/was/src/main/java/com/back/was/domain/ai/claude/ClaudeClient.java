package com.back.was.domain.ai.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.back.was.domain.ai.gemini.GeminiClient;
import com.back.was.domain.ai.gemini.GeminiErrors;
import com.back.was.domain.ai.gemini.GeminiMessage;
import com.back.was.domain.ai.gemini.GeminiTool;
import com.back.was.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
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
import java.util.function.Function;

/**
 * 실제 Claude 호출 (Messages API, 공식 Java SDK). claude.mock=false(기본)일 때만 빈으로 등록된다.
 *
 * <p>{@link GeminiClient} 인터페이스를 그대로 구현한다 — 넛지·코칭·TX-07·영수증 쪽 코드는 모델이 바뀐 걸
 * 모른다. 인터페이스·스키마 도우미 이름에 남은 "Gemini" 는 호출부를 건드리지 않으려고 둔 것이다.
 *
 * <p>Gemini 와 달라진 점:
 * <ul>
 *   <li><b>{@code temperature} 는 보내지 않는다.</b> 현재 Claude 모델은 sampling 파라미터를 400 으로 거절한다.
 *       인터페이스에는 남아 있지만 무시된다. 대신 {@code claude.effort} 로 생각 깊이를 정한다.</li>
 *   <li>JSON 강제는 {@code output_config.format}(구조화 출력). 스키마 변환은 {@link ClaudeSchema}.</li>
 *   <li>SDK 자체 재시도는 끈다({@code maxRetries(0)}). 재시도·예비 모델 전환은 아래 {@link #post} 가
 *       <b>호출 전체 시간 상한</b> 안에서만 한다 — 시도마다 상한을 주면 최악이 시도 수만큼 곱해진다
 *       (2026-11-15 Gemini 에서 약 55초까지 늘어난 적이 있다).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "claude", name = "mock", havingValue = "false", matchIfMissing = true)
public class ClaudeClient implements GeminiClient, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
    private static final int MAX_TOOL_ROUNDS = 5;

    /** 키가 없으면 null — 서버는 뜨고 호출 시점에 503. */
    private final AnthropicClient client;
    private final ClaudeProperties props;
    private final ObjectMapper objectMapper;

    public ClaudeClient(ClaudeProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        // 설정 상태만 로그로. 키 값 자체는 절대 찍지 않는다.
        if (props.hasApiKey()) {
            this.client = AnthropicOkHttpClient.builder()
                    .apiKey(props.apiKey())
                    .baseUrl(props.baseUrl())
                    .maxRetries(0)
                    .build();
            log.info("[Claude] 연결 준비 완료 - model={}, effort={}", props.model(), props.effort());
        } else {
            this.client = null;
            // 키가 없어도 서버는 뜨게 둔다 (AI 안 쓰는 팀원 작업 방해 X). 호출 시점에 503.
            log.warn("[Claude] ANTHROPIC_API_KEY 환경변수가 없습니다. AI 기능 호출 시 503(AI_UNAVAILABLE)이 반환됩니다.");
        }
    }

    @Override
    public JsonNode generateJson(String systemInstruction, List<GeminiMessage> messages,
                                 Map<String, Object> responseSchema, double temperature) {
        List<MessageParam> params = new ArrayList<>();
        for (GeminiMessage m : messages) {
            params.add(toParam(m));
        }
        return extractJson(post(props.timeout(), model -> jsonRequest(model, systemInstruction, params, responseSchema)));
    }

    @Override
    public JsonNode generateJson(String systemInstruction, String userText,
                                 byte[] imageBytes, String mimeType,
                                 Map<String, Object> responseSchema, double temperature) {
        // 이미지는 base64 로 본문에 직접 넣는다. 10MB 이하 영수증이면 충분 (API 요청 상한 32MB)
        ContentBlockParam image = ContentBlockParam.ofImage(ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.of(mimeType))
                        .data(Base64.getEncoder().encodeToString(imageBytes))
                        .build())
                .build());
        ContentBlockParam text = ContentBlockParam.ofText(TextBlockParam.builder().text(userText).build());
        List<MessageParam> params = List.of(MessageParam.ofUserOfContentBlockParams(List.of(image, text)));
        return extractJson(post(props.imageTimeout(), model -> jsonRequest(model, systemInstruction, params, responseSchema)));
    }

    @Override
    public String chatWithTools(String systemInstruction, List<GeminiMessage> messages,
                                List<GeminiTool> tools, double temperature) {
        Map<String, GeminiTool> byName = new LinkedHashMap<>();
        List<Tool> declared = new ArrayList<>();
        for (GeminiTool t : tools) {
            byName.put(t.name(), t);
            declared.add(toTool(t));
        }
        List<MessageParam> conversation = new ArrayList<>();
        for (GeminiMessage m : messages) {
            conversation.add(toParam(m));
        }

        // 왕복 전체에 상한 하나 — 라운드마다 새로 주면 최악이 라운드 수만큼 곱해진다
        Instant deadline = Instant.now().plus(props.timeout());
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            List<MessageParam> snapshot = List.copyOf(conversation);
            Message response = post(deadline, model -> {
                MessageCreateParams.Builder request = baseRequest(model, systemInstruction, snapshot);
                declared.forEach(request::addTool);
                return request.build();
            });

            List<ContentBlockParam> results = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                block.toolUse().ifPresent(use -> results.add(runTool(byName, use)));
            }
            if (results.isEmpty()) {
                String text = joinText(response);
                if (text.isEmpty()) throw GeminiErrors.parseFailed("AI 응답이 비어 있어요.");
                return text;
            }
            // 모델 턴은 생각 블록까지 그대로 되돌려 보낸다 — 빼거나 고치면 다음 요청이 거절될 수 있다
            conversation.add(response.toParam());
            conversation.add(MessageParam.ofUserOfContentBlockParams(results));
        }
        throw GeminiErrors.parseFailed("도구 호출이 끝나지 않았어요.");
    }

    @Override
    public void destroy() {
        if (client != null) client.close();
    }

    // ------------------------------------------------------------------ 요청 만들기

    private MessageCreateParams jsonRequest(String model, String system, List<MessageParam> messages,
                                            Map<String, Object> responseSchema) {
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        ClaudeSchema.toJsonSchema(responseSchema)
                .forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        return baseRequest(model, system, messages)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(props.effort()))
                        .format(JsonOutputFormat.builder().schema(schema.build()).build())
                        .build())
                .build();
    }

    private MessageCreateParams.Builder baseRequest(String model, String system, List<MessageParam> messages) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(props.maxTokens())
                .system(system)
                .messages(messages)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(props.effort())).build());
    }

    /** Gemini 의 "model" 역할이 Claude 의 "assistant". */
    private static MessageParam toParam(GeminiMessage m) {
        return "model".equals(m.role()) ? MessageParam.ofAssistant(m.text()) : MessageParam.ofUser(m.text());
    }

    @SuppressWarnings("unchecked")
    private static Tool toTool(GeminiTool t) {
        Map<String, Object> schema = t.parameters() == null
                ? Map.of("type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties", false)
                : ClaudeSchema.toJsonSchema(t.parameters());
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        ((Map<String, Object>) schema.get("properties"))
                .forEach((name, child) -> properties.putAdditionalProperty(name, JsonValue.from(child)));
        return Tool.builder()
                .name(t.name())
                .description(t.description())
                .inputSchema(Tool.InputSchema.builder()
                        .properties(properties.build())
                        .required((List<String>) schema.get("required"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    private ContentBlockParam runTool(Map<String, GeminiTool> byName, ToolUseBlock use) {
        GeminiTool tool = byName.get(use.name());
        ToolResultBlockParam.Builder result = ToolResultBlockParam.builder().toolUseId(use.id());
        try {
            if (tool == null) {
                result.content("없는 도구: " + use.name()).isError(true);
            } else {
                JsonNode args = objectMapper.valueToTree(use._input().convert(Map.class));
                result.content(objectMapper.writeValueAsString(tool.handler().apply(args)));
            }
        } catch (RuntimeException e) {
            result.content(String.valueOf(e.getMessage())).isError(true);
        }
        return ContentBlockParam.ofToolResult(result.build());
    }

    // ------------------------------------------------------------------ 호출

    private Message post(Duration limit, Function<String, MessageCreateParams> request) {
        return post(Instant.now().plus(limit), request);
    }

    /**
     * 모델별로 재시도하고, 계속 실패하면 예비 모델로 전환한다.
     * 429(속도 한도)는 모델마다 한도가 따로라 곧바로 예비 모델로 넘어간다. 5xx·529(과부하)·네트워크 오류는
     * 같은 모델로 재시도한 뒤 넘어간다. 400·401·403·404 는 설정 문제라 예비 모델도 소용없다.
     *
     * <p><b>전부 {@code deadline} 안에서만 일어난다.</b> 시도 하나의 타임아웃도 남은 시간으로 줄인다 — 이게 없으면
     * 상한 1초 전에 시작한 시도가 10초를 더 쓴다. 마감이 지나면 남은 재시도와 예비 모델을 건너뛰고 503.
     */
    private Message post(Instant deadline, Function<String, MessageCreateParams> request) {
        if (client == null) {
            throw GeminiErrors.unavailable("AI 서비스 설정이 되어 있지 않아요.");
        }
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
                    return client.messages().create(request.apply(model),
                            RequestOptions.builder().timeout(remaining).build());
                } catch (AnthropicServiceException e) {
                    int status = e.statusCode();
                    logHttpError(model, status, e.getMessage());
                    last = GeminiErrors.unavailable("AI 서비스를 일시적으로 사용할 수 없어요.", e);
                    if (status == 429) break;
                    if (status >= 500 && attempt < attempts && backOff(attempt, deadline)) continue;
                    if (status >= 500) break;
                    throw last;
                } catch (AnthropicException e) {
                    // 연결 실패·타임아웃·읽을 수 없는 응답 본문. 안 잡으면 503 이 아니라 500 이 된다.
                    log.warn("[Claude] {} 네트워크 오류/타임아웃 (시도 {}/{}): {}", model, attempt, attempts, e.getClass().getSimpleName());
                    last = GeminiErrors.unavailable("AI 서비스에 연결하지 못했어요.", e);
                    if (attempt < attempts && !backOff(attempt, deadline)) break;
                }
            }
            log.warn("[Claude] {} 응답 불가 → 예비 모델 확인", model);
        }
        throw last != null ? last : GeminiErrors.unavailable("AI 서비스를 일시적으로 사용할 수 없어요.");
    }

    /** 재시도 전 대기. 대기만으로 마감을 넘기면 기다리지 않고 {@code false} — 재시도할 시간이 없다. */
    private static boolean backOff(int attempt, Instant deadline) {
        long waitMillis = 1500L * attempt;
        if (!Instant.now().plusMillis(waitMillis).isBefore(deadline)) {
            return false;
        }
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ 응답 읽기

    private JsonNode extractJson(Message response) {
        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop) || StopReason.MAX_TOKENS.equals(stop)) {
            // 거절이면 스키마를 지키지 않을 수 있고, 토큰 상한이면 JSON 이 잘려 있다
            log.warn("[Claude] 응답을 쓸 수 없음. stop_reason={}", stop);
            throw GeminiErrors.parseFailed("입력에서 정보를 추출하지 못했어요.");
        }
        String raw = joinText(response);
        if (raw.isEmpty()) {
            log.warn("[Claude] 빈 응답. stop_reason={}", stop);
            throw GeminiErrors.parseFailed("입력에서 정보를 추출하지 못했어요.");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            // 원문은 사용자 입력을 담고 있을 수 있어 로그에 남기지 않는다
            log.warn("[Claude] JSON 파싱 실패 (길이 {})", raw.length());
            throw GeminiErrors.parseFailed("입력에서 정보를 추출하지 못했어요.", e);
        }
    }

    private static String joinText(Message response) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
        }
        return text.toString().trim();
    }

    /** 원인 파악용 로그. 상태 코드별 흔한 원인을 같이 남긴다 */
    private static void logHttpError(String model, int status, String message) {
        String hint = switch (status) {
            case 400 -> "요청 형식 오류 (스키마·파라미터 확인)";
            case 401 -> "API 키 오류 → ANTHROPIC_API_KEY 확인";
            case 403 -> "키 권한 없음";
            case 404 -> "모델명 오류 → CLAUDE_MODEL 확인";
            case 413 -> "요청이 너무 큼 (이미지 크기 확인)";
            case 429 -> "속도 한도 초과";
            case 529 -> "Claude 과부하";
            default -> status >= 500 ? "Claude 서버 장애" : "기타";
        };
        String shortMessage = message == null ? "" : message.substring(0, Math.min(message.length(), 300));
        log.warn("[Claude] {} HTTP {} ({}) {}", model, status, hint, shortMessage);
    }
}

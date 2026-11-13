package com.back.was.nudge;

import com.back.was.ai.gemini.GeminiClient;
import com.back.was.ai.gemini.GeminiSchema;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NDG-02 aiAnalysis / aiRecommendation 비동기 생성 (SFR-019).
 * - 넛지 저장 트랜잭션이 커밋된 뒤에 실행 (커밋 전이면 아직 행이 없어서)
 * - Gemini 실패 시 미리 준비한 템플릿 문구로 대체 (SRS 7.3-4)
 * 생성 중에는 두 필드가 null → 프론트 "AI가 분석 중이에요" 표시 후 재조회 (명세)
 */
@Service
public class NudgeAiCopyService {

    private static final Logger log = LoggerFactory.getLogger(NudgeAiCopyService.class);

    public record NudgeCopy(String aiAnalysis, String aiRecommendation) {}

    private static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> p = new LinkedHashMap<>();
        p.put("aiAnalysis", GeminiSchema.string("이번 주 소비 현황과 늘어난 원인. 1~2문장"));
        p.put("aiRecommendation", GeminiSchema.string("이번 주 안에 해볼 수 있는 구체적 행동 하나. 1~2문장"));
        SCHEMA = GeminiSchema.object(p);
    }

    private final GeminiClient gemini;
    private final NudgeStorePort store;

    public NudgeAiCopyService(GeminiClient gemini, NudgeStorePort store) {
        this.gemini = gemini;
        this.store = store;
    }

    /** 트랜잭션이 없을 때도 실행되게 fallbackExecution = true */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onCopyRequested(NudgeCopyRequested event) {
        NudgeCopy copy = generate(event.facts());
        try {
            store.saveAiCopy(event.nudgeId(), copy.aiAnalysis(), copy.aiRecommendation());
        } catch (Exception e) {
            log.warn("[NDG-02] AI 문구 저장 실패 nudgeId={}: {}", event.nudgeId(), e.getMessage());
        }
    }

    /** 실패해도 예외를 던지지 않고 대체 문구를 돌려준다 */
    public NudgeCopy generate(NudgeFacts f) {
        try {
            JsonNode r = gemini.generateJson(NudgePrompts.COPY_SYSTEM, NudgePrompts.data(f), SCHEMA, 0.6);
            String analysis = r.path("aiAnalysis").asString("").strip();
            String recommendation = r.path("aiRecommendation").asString("").strip();
            if (analysis.isEmpty() || recommendation.isEmpty()) return fallback(f);
            return new NudgeCopy(analysis, recommendation);
        } catch (Exception e) {
            log.warn("[NDG-02] Gemini 실패 → 대체 문구 사용: {}", e.getMessage());
            return fallback(f);
        }
    }

    static NudgeCopy fallback(NudgeFacts f) {
        return new NudgeCopy(
                String.format("이번 주 %s 지출이 평소보다 %,d원 많아요.", f.categoryName(), Math.max(f.diffAmount(), 0)),
                String.format("남은 기간 %s 지출 한도를 미리 정해 두면 도움이 돼요.", f.categoryName()));
    }
}

package com.back.was.eval;

import com.back.was.coach.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 오프라인 평가(키 불필요): "LLM 이 숫자를 지어내면 검증기가 잡아내는가".
 * 검증을 통과한 저장 인사이트에 일부러 가짜 숫자·누락을 주입하고 탐지율을 잰다. 정상 입력의 오탐도 함께 확인.
 */
class ValidatorDetectionEval {

    private final ObjectMapper om = JsonMapper.builder().build();

    @Test
    void detectsInjectedHallucinations() throws Exception {
        DemoData demo = new DemoData(om);
        Aggregator agg = new Aggregator(demo);
        InsightValidator validator = new InsightValidator(new NumberCheck(om));

        int clean = 0, cleanFalsePositive = 0, injected = 0, caught = 0;
        List<String> rows = new ArrayList<>();
        for (var p : demo.all()) {
            Summary s = agg.summarize(p.id());
            InsightReport base;
            try (InputStream in = getClass().getResourceAsStream("/demo/insights/" + p.id() + ".json")) {
                base = om.treeToValue(om.readTree(in).get("report"), InsightReport.class);
            }
            clean++;
            if (!validator.validate(base, s).isEmpty()) cleanFalsePositive++;

            int pInjected = 0, pCaught = 0;
            for (int i = 0; i < base.insights().size(); i++) {
                for (int mode = 0; mode < 3; mode++) {
                    InsightReport mutated = mutate(base, i, mode);
                    if (mutated == null) continue;
                    pInjected++;
                    if (!validator.validate(mutated, s).isEmpty()) pCaught++;
                }
            }
            injected += pInjected;
            caught += pCaught;
            rows.add(String.format("| %s | %d | %d | %.0f%% |", p.id(), pInjected, pCaught, pInjected == 0 ? 100.0 : 100.0 * pCaught / pInjected));
        }
        String md = "# 숫자 검증기 탐지율 (오프라인)\n\n주입 유형: ① 본문에 지어낸 금액 ② 근거(evidence) 값 변조 ③ 인사이트 삭제(개수·핵심 항목 누락)\n\n"
                + "| 페르소나 | 주입 | 탐지 | 탐지율 |\n|---|---|---|---|\n" + String.join("\n", rows) + "\n\n"
                + String.format("**전체 탐지율 %.1f%% (%d/%d)**, 정상 입력 오탐 %d/%d\n", 100.0 * caught / injected, caught, injected, cleanFalsePositive, clean);
        EvalReport.write("validator-detection.md", md);
        assertEquals(0, cleanFalsePositive, "정상 입력을 잘못 거부함");
        assertEquals(injected, caught, "주입한 오류를 못 잡음");
    }

    private InsightReport mutate(InsightReport r, int idx, int mode) {
        List<InsightReport.Insight> list = new ArrayList<>(r.insights());
        InsightReport.Insight in = list.get(idx);
        switch (mode) {
            case 0 -> list.set(idx, new InsightReport.Insight(in.title(), in.category(), in.severity(),
                    in.message() + " 이 추세면 1년에 8,765,432원이에요.", in.evidence(), in.action()));
            case 1 -> {
                if (in.evidence().isEmpty()) return null;
                List<InsightReport.Evidence> ev = new ArrayList<>(in.evidence());
                var e = ev.get(0);
                ev.set(0, new InsightReport.Evidence(e.label(), e.value() * 1.37 + 7, e.unit()));
                list.set(idx, new InsightReport.Insight(in.title(), in.category(), in.severity(), in.message(), ev, in.action()));
            }
            default -> list.remove(idx);
        }
        // 인사이트를 지워도 3~4개 규칙/핵심 항목 누락 규칙에 걸려야 한다 (4→3 개일 땐 핵심 항목 누락으로 잡혀야 함)
        return new InsightReport(r.headline(), list);
    }
}

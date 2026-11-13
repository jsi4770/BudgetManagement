package com.back.was.coach;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 목표 현실성 판정(코드)이 원본 Python(goals.assess)과 같은지 확인 — 여유/빠듯/지출조정/불가/월한도 케이스 포함 */
class GoalAssessGoldenTest {

    @Test
    void assessmentsMatchPython() {
        DemoData demo = TestSupport.demo();
        GoalService svc = new GoalService(null, new Aggregator(demo), demo, null, null, TestSupport.OM);
        JsonNode cases = TestSupport.golden("assess_cases.json");
        int n = 0;
        for (var e : cases.properties()) {
            String persona = e.getKey().split("\\|")[0];
            GoalService.GoalIntent intent = TestSupport.OM.treeToValue(e.getValue().get("intent"), GoalService.GoalIntent.class);
            var actual = TestSupport.OM.valueToTree(svc.assess(intent, persona, e.getValue().get("start").asString()));
            assertNull(TestSupport.diff(e.getValue().get("assessment"), actual, e.getKey()), e.getKey());
            n++;
        }
        assertTrue(n >= 10);
    }
}

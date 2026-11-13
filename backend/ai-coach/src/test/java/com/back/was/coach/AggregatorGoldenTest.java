package com.back.was.coach;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNull;

/** Java 집계 결과가 원본 Python(aggregate.summarize)과 모든 필드에서 같은지 확인 */
class AggregatorGoldenTest {

    @ParameterizedTest
    @ValueSource(strings = {"college_delivery", "rookie_subscription", "frugal_saver"})
    void summaryMatchesPython(String persona) {
        var actual = TestSupport.OM.valueToTree(new Aggregator(TestSupport.demo()).summarize(persona));
        assertNull(TestSupport.diff(TestSupport.golden("summary_" + persona + ".json"), actual, "$"));
    }
}

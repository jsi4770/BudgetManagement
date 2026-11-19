package com.back.was.domain.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.back.was.domain.ai.AiTestFixtures.CATEGORIES;
import static org.assertj.core.api.Assertions.assertThat;

/** 프롬프트에 서버 기준 날짜표와 DB 카테고리가 들어가는지 — 모델의 시간 감각에 맡기지 않는다. */
class ParsePromptBuilderTest {

    private static final LocalDate BASE = LocalDate.of(2026, 11, 10);   // 화요일

    @Test
    @DisplayName("날짜표: '저번주 수요일'은 서버가 계산해 둔 2026-11-04 를 고르면 된다")
    void dateTableHasLastWeekWednesday() {
        String table = ParsePromptBuilder.dateTable(BASE);

        assertThat(table).contains("오늘(작성일): 2026-11-10 (화)");
        assertThat(table).contains("어제: 2026-11-09");
        assertThat(table).contains("저번 주(지난주): 월 2026-11-02, 화 2026-11-03, 수 2026-11-04");
    }

    @Test
    @DisplayName("카테고리는 id·이름·type 으로 들어가고, 기타는 지출 id 로 안내된다")
    void promptListsDbCategoriesAndExpenseEtc() {
        String prompt = new ParsePromptBuilder().build(BASE, CATEGORIES);

        assertThat(prompt).contains("- id=2 | 배달 | EXPENSE");
        assertThat(prompt).contains("- id=9 | 기타 | INCOME");
        assertThat(prompt).contains("'기타'(id=6)");
        assertThat(prompt).doesNotContain("{ETC_ID}").doesNotContain("{CATEGORIES}").doesNotContain("{TODAY}");
    }
}

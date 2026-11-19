package com.back.was.nudge;

/** title 은 DB 미저장, 서버 템플릿 조합: "{카테고리} 지출이 평소보다 늘었어요" (NDG-01) */
public final class NudgeTitle {
    private NudgeTitle() {}

    public static String of(String categoryName) {
        return categoryName + " 지출이 평소보다 늘었어요";
    }
}

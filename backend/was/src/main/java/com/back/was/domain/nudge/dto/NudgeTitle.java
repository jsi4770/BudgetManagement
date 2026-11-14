package com.back.was.domain.nudge.dto;

import com.back.was.domain.category.Category;

/**
 * 넛지 카드 제목. <b>DB 에 저장하지 않고</b> 카테고리명으로 조합한다 (ERD 6장 · 명세
 * NDG-01 "DB 미저장, 서버 템플릿 조합").
 *
 * <p>저장하지 않는 이유: 카테고리명이 바뀌면 과거 카드의 제목만 옛 이름으로 남는다.
 *
 * <p>NDG-01·02·03 세 응답이 같은 문구를 쓰므로 템플릿 문자열을 한 곳에 둔다 — 세 곳에
 * 복사해 두면 한쪽만 고쳐져 화면마다 제목이 달라진다.
 */
final class NudgeTitle {

    private static final String TEMPLATE = "%s 지출이 평소보다 늘었어요";

    private NudgeTitle() {
    }

    static String of(Category category) {
        return TEMPLATE.formatted(category.getName());
    }
}

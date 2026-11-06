package com.back.was.domain.ai;

import com.back.was.domain.category.dto.CategoryResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link TransactionTextParser} 입력. 어댑터가 프롬프트를 만드는 데 필요한 모든 것이
 * 여기 들어 있어서, 어댑터는 DB 를 몰라도 된다.
 *
 * @param text       사용자가 입력한 한 줄 (명세 TX-07 요청의 {@code text})
 * @param today      오늘(KST). "어제"처럼 상대 날짜를 해석하는 기준이다 — 명세 TX-07 은
 *                   <b>서버 기준</b> 오늘로 계산하라고 하므로 모델의 시간 감각에 맡기지
 *                   않고 넣어서 보낸다.
 * @param categories 고를 수 있는 카테고리 전체 (서버_내부_처리 8장: DB 목록을 프롬프트에
 *                   포함하고 그중 하나만 선택, 해당 없으면 "기타"). {@code id} 로 고르게
 *                   해야 한다 — "기타"는 INCOME · EXPENSE 양쪽에 있어 이름만으로는
 *                   가려낼 수 없다.
 */
public record TextParseCommand(
        String text,
        LocalDate today,
        List<CategoryResponse> categories
) {
}

package com.back.was.domain.ai;

/**
 * 코칭 답변에서 추천할 수 있는 <b>내 지역 가맹점 후보</b> 한 건. AI 는 이 목록의 {@code id} 중에서만 고른다
 * ({@code categoryId} 와 같은 방식 — 어댑터는 DB 를 볼 수 없고, 모델이 없는 가맹점을 지어내면 안 된다).
 *
 * @param id           {@code local_merchants.id}
 * @param name         상호
 * @param groupLabel   업종 묶음 이름(식당·주점, 카페 …)
 * @param categoryName 이 후보가 이어지는 가계부 카테고리 이름 — "식비 지출이 많으니 식당을 추천" 같은 연결용
 */
public record MerchantCandidate(Long id, String name, String groupLabel, String categoryName) {
}

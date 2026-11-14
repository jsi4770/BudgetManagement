package com.back.was.domain.ai;

/**
 * 코칭 첫 메시지를 만드는 데 필요한 값 (COACH-01).
 *
 * <p>{@code docs/AI_대체_문구.md} 3장은 문구를 <b>두 갈래</b>로 나눈다 — 넛지에서 시작한
 * 경우와 그냥 시작한 경우. 그 갈림길이 {@link #nudge()} 의 존재 여부다.
 *
 * <p>넛지 맥락을 {@code categoryName}·{@code spendingAmount}·{@code averageAmount} 세 개의
 * nullable 필드로 늘어놓지 않고 {@link NudgeContext} 로 묶은 이유: 셋은 함께 있거나 함께
 * 없다. 따로 두면 "카테고리는 있는데 금액은 {@code null}" 같은 반쪽 상태가 타입상 가능해지고,
 * 그걸 문구에 채우면 사용자 화면에 {@code null원} 이 나간다.
 *
 * <p>엔티티({@code Nudge}·{@code User})를 넘기지 않는 건 {@code NudgeTextCommand} 와 같은
 * 이유다 — AI 어댑터는 DB·JPA 를 몰라야 한다.
 *
 * @param username {@code {username}} — 이름으로 부르는 인사말에 쓰인다
 * @param nudge    넛지에서 시작했으면 그 카드의 숫자, 아니면 {@code null}
 */
public record CoachingOpeningCommand(
        String username,
        NudgeContext nudge
) {
    /**
     * 넛지 카드가 들고 있는 숫자 — {@code docs/AI_대체_문구.md} 3장 "넛지에서 시작한 경우"
     * 문구의 변수들이다.
     *
     * @param categoryName   {@code {category}}
     * @param spendingAmount {@code {amount}} — 이번 주 금액
     * @param averageAmount  {@code {avg}} — 평소 이맘때 평균
     */
    public record NudgeContext(
            String categoryName,
            int spendingAmount,
            int averageAmount
    ) {
        /** {@code {diff}} — 금액 − 평균. 넛지가 생겼다는 건 이 값이 30,000원 이상이라는 뜻이다. */
        public int difference() {
            return spendingAmount - averageAmount;
        }
    }

    /** 넛지 없이 시작한 코칭 (명세 COACH-01 에서 {@code nudgeId} 를 보내지 않은 경우). */
    public static CoachingOpeningCommand withoutNudge(String username) {
        return new CoachingOpeningCommand(username, null);
    }

    /** 넛지 카드의 "코칭 시작" 버튼으로 들어온 코칭. */
    public static CoachingOpeningCommand fromNudge(String username, String categoryName,
                                                   int spendingAmount, int averageAmount) {
        return new CoachingOpeningCommand(
                username, new NudgeContext(categoryName, spendingAmount, averageAmount));
    }

    public boolean hasNudgeContext() {
        return nudge != null;
    }
}

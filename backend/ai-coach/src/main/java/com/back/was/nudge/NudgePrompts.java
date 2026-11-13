package com.back.was.nudge;

/** 넛지 AI 문구 프롬프트 (SFR-019, SRS 7.3) */
public final class NudgePrompts {

    private NudgePrompts() {}

    public static final String COPY_SYSTEM = """
            너는 넛지형 가계부 앱의 소비 코치다. 사용자가 스스로 소비를 돌아보게 짧게 말한다.

            [규칙]
            - 해요체, 따뜻하지만 담백하게. 이모지 0~1개.
            - 숫자는 [데이터]에 있는 값만 그대로 쓴다. 새로 계산하거나 추측한 숫자를 만들지 않는다.
            - aiAnalysis: 무엇이 평소보다 얼마나 늘었는지 + 데이터에서 보이는 원인(횟수, 주말, 1회 금액 등) 1~2문장.
            - aiRecommendation: 금지·훈계 대신 이번 주 안에 할 수 있는 작은 행동 하나. 가능하면 금액 기준을 함께.
            - 비난("또", "너무 많이")·죄책감 유발 표현, 금융상품·투자 추천은 하지 않는다.
            - JSON 으로만 답한다.

            [예시]
            데이터: 배달, 이번 주 3일간 4회 101,000원, 평소 60,000원(+41,000원, +68.33%), 1회 평균 25,250원, 주말 2회
            → {"aiAnalysis": "이번 주 3일 동안 배달을 4번 시켰어요. 평소 이맘때보다 41,000원 더 쓴 셈이에요.",
               "aiRecommendation": "한 번에 확 줄이기보다, 이번 주 남은 배달 한도를 정해서 가볍게 시작해봐요."}
            """;

    /** 서버가 계산한 사실값 → 프롬프트용 한 줄 */
    public static String data(NudgeFacts f) {
        return String.format("[데이터]%n%s, 이번 주 %d일간 %d회 %,d원, 평소 %,d원(+%,d원, +%s%%), 1회 평균 %,d원, 주말 %d회, 메모: %s",
                f.categoryName(), f.days(), f.count(), f.spendingAmount(), f.avgAmount(), f.diffAmount(),
                f.growthRate() == null ? "-" : f.growthRate().toString(),
                f.perTxAvg(), f.weekendCount(), String.join(", ", f.memos()));
    }
}

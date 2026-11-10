package com.back.was.domain.challenge;

import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ERD 9장 CHALLENGE 의 규칙 — 확정 스냅샷과 종료 판정.
 *
 * <p>DB 없이 돈다. 카테고리는 {@code null} 로 둔다 — {@code Category.of} 가 패키지 전용이고,
 * 여기서 보려는 규칙(목표 유형·기간·판정)은 카테고리와 무관하다. 컬럼 자체는
 * {@code NOT NULL} 이라 실제 저장 경로는 {@code CoachingStartApiTest} 가 통과시킨다.
 */
class ChallengeTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 4);
    private static final LocalDate END = LocalDate.of(2026, 11, 10);

    private static Coaching coaching() {
        return Coaching.start(User.signUp("홍길동", "challenge-unit@email.com", "irrelevant-hash"));
    }

    private static Challenge amountChallenge(int goalAmount) {
        return Challenge.of(coaching(), null, "배달 50,000원 이내", GoalType.AMOUNT,
                goalAmount, null, START, END);
    }

    @Test
    @DisplayName("확정 직후에는 실적이 0 이고 진행 중이다 — 후처리 2번이 채울 값들")
    void startsWithZeroPerformance() {
        Challenge challenge = amountChallenge(50_000);

        assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
        assertThat(challenge.isFinished()).isFalse();
        assertThat(challenge.getSpendingAmount()).isZero();
        assertThat(challenge.getSpendingCount()).isZero();
        assertThat(challenge.getAchievementRate()).isEqualByComparingTo(BigDecimal.ZERO);
        // DECIMAL(10,2) 컬럼이라 scale 이 2 여야 한다 (CLAUDE.md: 비율은 scale 2)
        assertThat(challenge.getAchievementRate().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("목표 유형과 맞지 않는 쪽 목표 값은 비운다 (ERD 9장 체크 조건)")
    void keepsOnlyTheGoalMatchingTheType() {
        Challenge amount = Challenge.of(coaching(), null, "배달 50,000원 이내", GoalType.AMOUNT,
                50_000, 3, START, END);
        Challenge count = Challenge.of(coaching(), null, "배달 2회 이하", GoalType.COUNT,
                50_000, 2, START, END);

        assertThat(amount.getGoalAmount()).isEqualTo(50_000);
        assertThat(amount.getGoalCount()).isNull();
        assertThat(count.getGoalCount()).isEqualTo(2);
        assertThat(count.getGoalAmount()).isNull();
    }

    @Test
    @DisplayName("유형에 맞는 목표 값이 없으면 확정할 수 없다")
    void requiresTheGoalMatchingTheType() {
        assertThatThrownBy(() -> Challenge.of(coaching(), null, "배달 목표", GoalType.AMOUNT,
                null, 2, START, END))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Challenge.of(coaching(), null, "배달 목표", GoalType.COUNT,
                50_000, null, START, END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞이면 거부한다 (ERD 9장 CHECK)")
    void rejectsReversedPeriod() {
        assertThatThrownBy(() -> Challenge.of(coaching(), null, "배달 목표", GoalType.AMOUNT,
                50_000, null, END, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("하루짜리 기간은 허용한다 — CHECK 는 '이후'가 아니라 '이상'이다")
    void allowsASingleDayPeriod() {
        assertThat(Challenge.of(coaching(), null, "배달 목표", GoalType.AMOUNT,
                50_000, null, START, START).getEndDate()).isEqualTo(START);
    }

    @Nested
    @DisplayName("실적 갱신 (후처리 2번 — 서버_내부_처리 2장)")
    class UpdatePerformance {

        @Test
        @DisplayName("ACHIEVEMENT_RATE = SPENDING ÷ GOAL × 100 (시연 5장 7단계: 24,000 / 50,000)")
        void computesTheAchievementRate() {
            Challenge challenge = amountChallenge(50_000);

            challenge.updatePerformance(24_000, 1);

            assertThat(challenge.getSpendingAmount()).isEqualTo(24_000);
            assertThat(challenge.getSpendingCount()).isEqualTo(1);
            assertThat(challenge.getAchievementRate()).isEqualByComparingTo("48.00");
        }

        @Test
        @DisplayName("COUNT 목표는 횟수로 비율을 낸다 — finish() 가 보는 짝과 같아야 한다")
        void countGoalUsesTheCount() {
            Challenge challenge = Challenge.of(coaching(), null, "배달 2회 이하", GoalType.COUNT,
                    null, 2, START, END);

            challenge.updatePerformance(99_000, 3);

            // 금액은 저장되지만 비율은 횟수 기준이다 (3 / 2 = 150%)
            assertThat(challenge.getSpendingAmount()).isEqualTo(99_000);
            assertThat(challenge.getAchievementRate()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("100% 를 넘겨도 자르지 않는다 — 얼마나 넘겼는지가 사라지면 안 된다")
        void doesNotCapTheRate() {
            Challenge challenge = amountChallenge(50_000);

            challenge.updatePerformance(56_000, 3);

            assertThat(challenge.getAchievementRate()).isEqualByComparingTo("112.00");
            // 초과해도 진행 중이다 — 자동 실패·취소는 명세에 없다 (ERD 9장)
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("비율은 scale 2 반올림 (10,000 / 30,000 → 33.33)")
        void roundsToTwoDecimals() {
            Challenge challenge = amountChallenge(30_000);

            challenge.updatePerformance(10_000, 1);

            assertThat(challenge.getAchievementRate()).isEqualByComparingTo("33.33");
            assertThat(challenge.getAchievementRate().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("누적이 아니라 덮어쓴다 — 거래를 지우면 실적이 줄어야 한다")
        void overwritesInsteadOfAccumulating() {
            Challenge challenge = amountChallenge(50_000);
            challenge.updatePerformance(24_000, 1);

            challenge.updatePerformance(0, 0);

            assertThat(challenge.getSpendingAmount()).isZero();
            assertThat(challenge.getSpendingCount()).isZero();
            assertThat(challenge.getAchievementRate()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("종료된 챌린지는 재계산하지 않는다 (서버_내부_처리 1장 '늦은 기록')")
        void refusesToUpdateAFinishedChallenge() {
            Challenge challenge = amountChallenge(50_000);
            challenge.finish();

            assertThatThrownBy(() -> challenge.updatePerformance(99_000, 5))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("종료 판정 (ERD 9장 / SRS 7.4-4 — 일일 스케줄러의 자리)")
    class Finish {

        @Test
        @DisplayName("AMOUNT: 실적이 목표 이하면 SUCCESS")
        void amountGoalWithinBudgetSucceeds() {
            Challenge challenge = amountChallenge(50_000);

            challenge.finish();

            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.SUCCESS);
            assertThat(challenge.isFinished()).isTrue();
        }

        @Test
        @DisplayName("AMOUNT: 실적이 목표를 넘으면 FAILED")
        void amountGoalOverBudgetFails() {
            Challenge challenge = amountChallenge(50_000);
            challenge.updatePerformance(56_000, 3);

            challenge.finish();

            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        }

        @Test
        @DisplayName("경계: 실적이 목표와 같으면 SUCCESS ('목표 이하'는 같은 값을 포함)")
        void spendingEqualToTheGoalSucceeds() {
            Challenge challenge = amountChallenge(50_000);
            challenge.updatePerformance(50_000, 2);

            challenge.finish();

            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.SUCCESS);
        }

        @Test
        @DisplayName("COUNT: 보는 실적이 횟수로 바뀐다 — 금액이 아무리 적어도 횟수로 판정한다")
        void countGoalReadsTheCountPerformance() {
            Challenge challenge = Challenge.of(coaching(), null, "배달 2회 이하", GoalType.COUNT,
                    null, 2, START, END);
            challenge.updatePerformance(1_000, 3);

            challenge.finish();

            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        }

        @Test
        @DisplayName("이미 종료된 챌린지는 다시 판정하지 않는다 (ERD 9장 '늦은 기록은 반영하지 않아요')")
        void refusesToJudgeTwice() {
            Challenge challenge = amountChallenge(50_000);
            challenge.finish();

            assertThatThrownBy(challenge::finish)
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}

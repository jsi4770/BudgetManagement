package com.back.was.domain.coaching;

import com.back.was.domain.coaching.dto.CoachingProposalResponse;
import com.back.was.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ERD 7장 COACHING 의 규칙 — 상태 전이와 "최신 제안 1개만 보관".
 *
 * <p>DB 없이 돈다. 제안 카테고리는 {@code null} 로 둔다 — {@code Category.of} 가
 * 패키지 전용이고, 여기서 보려는 규칙(목표 유형·기간·덮어쓰기)은 카테고리와 무관하다.
 * ERD 7장도 {@code PROPOSAL_CATEGORY_ID} 를 NULL 허용으로 두었다.
 */
class CoachingTest {

    private static Coaching coaching() {
        return Coaching.start(User.signUp("홍길동", "coaching-unit@email.com", "irrelevant-hash"));
    }

    @Test
    @DisplayName("시작하면 IN_PROGRESS 고 제안은 비어 있다 (명세 COACH-01 proposal: null)")
    void startsInProgressWithoutProposal() {
        Coaching coaching = coaching();

        assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.IN_PROGRESS);
        assertThat(coaching.isEnded()).isFalse();
        assertThat(coaching.hasProposal()).isFalse();
        assertThat(CoachingProposalResponse.from(coaching)).isNull();
    }

    @Nested
    @DisplayName("취소 (ERD 7장 '새 코칭을 시작하면 이전 IN_PROGRESS 를 CANCELED 로')")
    class Cancel {

        @Test
        @DisplayName("진행 중이던 세션은 CANCELED 가 되고 종료로 취급된다")
        void cancelsInProgress() {
            Coaching coaching = coaching();

            coaching.cancel();

            assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.CANCELED);
            assertThat(coaching.isEnded()).isTrue();
        }

        @Test
        @DisplayName("이미 종료된 세션은 다시 취소할 수 없다 — 확정한 챌린지의 주인이 취소된 세션에 걸린다")
        void refusesToCancelTwice() {
            Coaching coaching = coaching();
            coaching.cancel();

            assertThatThrownBy(coaching::cancel)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("확정으로 종료 (COACH-04)")
    class Complete {

        @Test
        @DisplayName("COMPLETED 가 되고 종료로 취급된다")
        void completesInProgress() {
            Coaching coaching = coaching();

            coaching.complete();

            assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.COMPLETED);
            assertThat(coaching.isEnded()).isTrue();
        }

        @Test
        @DisplayName("이미 종료된 세션은 확정할 수 없다 — 챌린지가 두 개 생기면 ERD 9장을 깬다")
        void refusesToCompleteTwice() {
            Coaching completed = coaching();
            completed.complete();
            Coaching canceled = coaching();
            canceled.cancel();

            assertThatThrownBy(completed::complete).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(canceled::complete).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("확정해도 제안 값은 남는다 — 'AI 제안을 그대로 확정한 비율' 기록용이다")
        void keepsTheProposalAfterCompleting() {
            Coaching coaching = coaching();
            coaching.propose(null, "배달 40,000원 이내", GoalType.AMOUNT, 40_000, null, 7);

            coaching.complete();

            assertThat(coaching.getProposalGoalAmount()).isEqualTo(40_000);
        }
    }

    @Nested
    @DisplayName("목표 제안 (ERD 7장 '최신 제안 1개만 보관')")
    class Propose {

        @Test
        @DisplayName("AMOUNT 제안은 금액만 채우고 횟수는 비운다 (ERD 7장 체크 조건)")
        void proposesAmountGoal() {
            Coaching coaching = coaching();

            coaching.propose(null, "배달 50,000원 이내", GoalType.AMOUNT, 50_000, null, 7);

            assertThat(coaching.hasProposal()).isTrue();
            assertThat(CoachingProposalResponse.from(coaching)).isEqualTo(
                    new CoachingProposalResponse(null, "AMOUNT", 50_000, null, 7, "배달 50,000원 이내"));
        }

        @Test
        @DisplayName("다시 제안하면 덮어쓴다 — 유형이 바뀌면 반대쪽 목표 값도 비운다")
        void overwritesThePreviousProposal() {
            Coaching coaching = coaching();
            coaching.propose(null, "배달 50,000원 이내", GoalType.AMOUNT, 50_000, null, 7);

            coaching.propose(null, "배달 2회 이하", GoalType.COUNT, null, 2, 14);

            // goalAmount 가 남아 있으면 화면에 금액과 횟수가 동시에 뜬다
            assertThat(CoachingProposalResponse.from(coaching)).isEqualTo(
                    new CoachingProposalResponse(null, "COUNT", null, 2, 14, "배달 2회 이하"));
        }

        @Test
        @DisplayName("유형에 맞는 목표 값이 없으면 거부한다 (AMOUNT 인데 금액이 null)")
        void requiresTheGoalMatchingTheType() {
            Coaching coaching = coaching();

            assertThatThrownBy(() ->
                    coaching.propose(null, "배달 목표", GoalType.AMOUNT, null, 2, 7))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() ->
                    coaching.propose(null, "배달 목표", GoalType.COUNT, 50_000, null, 7))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("0 이하의 목표·기간은 거부한다 (ERD 7장 CHECK > 0)")
        void rejectsNonPositiveValues() {
            Coaching coaching = coaching();

            assertThatThrownBy(() ->
                    coaching.propose(null, "배달 목표", GoalType.AMOUNT, 0, null, 7))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() ->
                    coaching.propose(null, "배달 목표", GoalType.AMOUNT, 50_000, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("거부된 제안은 이전 제안을 망가뜨리지 않는다 — 검증이 먼저다")
        void keepsThePreviousProposalWhenRejected() {
            Coaching coaching = coaching();
            coaching.propose(null, "배달 50,000원 이내", GoalType.AMOUNT, 50_000, null, 7);

            assertThatThrownBy(() ->
                    coaching.propose(null, "망가진 제안", GoalType.COUNT, null, null, 7))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(CoachingProposalResponse.from(coaching)).isEqualTo(
                    new CoachingProposalResponse(null, "AMOUNT", 50_000, null, 7, "배달 50,000원 이내"));
        }
    }
}

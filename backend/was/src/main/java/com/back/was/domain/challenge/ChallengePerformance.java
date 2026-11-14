package com.back.was.domain.challenge;

import com.back.was.domain.category.Category;
import com.back.was.domain.transaction.SpendingTotal;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 거래 변경 후처리 <b>2번</b>: 진행 중 챌린지 실적 재계산 (서버_내부_처리 1장 순서 2번 ·
 * 2장).
 *
 * <p>{@code TransactionService} 가 <b>같은 트랜잭션 안에서 동기로</b> 부른다 —
 * {@code NudgeJudgement} 와 나란히 있고 이유도 같다. 거래 저장과 실적이 한 커밋에 묶여야
 * "거래는 들어갔는데 챌린지 진행률만 안 움직인" 상태가 없다.
 *
 * <p><b>전체 재집계 방식이라 멱등하다.</b> 기간 안의 거래를 다시 더해서 덮어쓰므로 같은
 * 인자로 두 번 불러도 결과가 같고, 등록·수정·삭제가 모두 같은 코드를 탄다. 그래서
 * TX-05 가 수정 전·후를 각각 한 번씩 부르면 되고, 호출자가 둘을 비교해 중복을 없앨 필요가
 * 없다({@code NudgeJudgement.judge} 가 ID 로 중복을 없애는 것과 다른 선택인데, 그쪽은
 * 이벤트를 쏘기 때문에 두 번 도는 것이 공짜가 아니다).
 *
 * <p><b>상태는 절대 건드리지 않는다.</b> 목표를 초과해도 자동 실패·취소가 없다 — 판정은
 * 기간이 끝난 다음 날 00:05 의 스케줄러가 한 번만 하고(ERD 9장), 챌린지 상태에
 * {@code CANCELED} 라는 값도 없다. 자세한 설명은 {@link Challenge#updatePerformance}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengePerformance {

    private final ChallengeRepository challengeRepository;
    private final TransactionRepository transactionRepository;

    /**
     * 그 사용자·카테고리·날짜에 걸리는 진행 중 챌린지의 실적을 다시 계산한다.
     *
     * <p>인자가 <b>거래의</b> 카테고리와 날짜라는 점이 중요하다. 그 조합으로 재계산할
     * 챌린지를 고르고(서버_내부_처리 2장), 고른 챌린지의 <b>기간 전체</b>를 다시 집계한다 —
     * 방금 바뀐 거래 한 건만 더하는 것이 아니다. 그래서 수정으로 날짜가 기간 밖으로
     * 나갔을 때도 <b>이전 날짜</b>로 한 번 불러 주면 그 거래가 빠진 합계로 갱신된다.
     *
     * <p>수입 카테고리는 걸리는 챌린지가 없어 자동으로 아무 일도 하지 않는다 — 챌린지의
     * 카테고리는 확정 시점에 지출로 검증되기 때문이다(COACH-04). 월세도 같다: 챌린지로
     * 확정될 수는 있지만 그건 사용자의 선택이고, 넛지처럼 제외하는 규칙은 2장에 없다.
     *
     * <p>조회가 {@code IN_PROGRESS} 로 제한되므로 종료된 챌린지는 손대지 않는다 —
     * 종료 시점에 확정된 결과다(서버_내부_처리 1장).
     *
     * @param category 거래의 카테고리. {@code null} 이면 아무 일도 하지 않는다 (TX-05 가
     *                 수정 전 값을 그대로 넘길 수 있게)
     * @param date     거래의 날짜. {@code null} 이면 아무 일도 하지 않는다
     */
    public void recalculate(User user, Category category, LocalDate date) {
        if (category == null || date == null) {
            return;
        }

        challengeRepository
                .findCovering(user.getId(), ChallengeStatus.IN_PROGRESS, category.getId(), date)
                .forEach(challenge -> recalculate(user, challenge));
    }

    /**
     * 챌린지 기간 전체를 다시 집계해 세 컬럼을 덮어쓴다.
     *
     * <p>집계 범위는 거래의 날짜가 아니라 <b>챌린지의 {@code START_DATE} ~
     * {@code END_DATE}</b> 다 (서버_내부_처리 2장 "챌린지 기간 내 거래만 포함", SRS 7.4-2).
     * 기간 밖의 같은 카테고리 지출은 실적에 들어가지 않는다.
     *
     * <p>{@code Math.toIntExact} 로 좁히는 건 TX-03 의 일별 합계와 같은 판단이다 — 한
     * 챌린지 기간의 합계가 int 범위를 넘었다면 이미 명세의 금액 타입 밖이고, 조용히 틀린
     * 숫자를 저장하는 것보다 터지는 게 낫다.
     */
    private void recalculate(User user, Challenge challenge) {
        SpendingTotal total = transactionRepository.sumCategorySpending(
                user.getId(),
                challenge.getCategory().getId(),
                challenge.getStartDate(),
                challenge.getEndDate());

        challenge.updatePerformance(
                Math.toIntExact(total.amount()), Math.toIntExact(total.count()));

        log.debug("챌린지 {} 실적 갱신: {}원 / {}회", challenge.getId(), total.amount(), total.count());
    }
}

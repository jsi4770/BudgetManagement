package com.back.was.domain.budget;

import com.back.was.domain.budget.dto.BudgetResponse;
import com.back.was.domain.budget.dto.SaveBudgetRequest;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.support.YearMonths;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Optional;

/**
 * BUDGET-01 월 예산·목표 조회, BUDGET-02 저장.
 *
 * <p>모든 조회·저장이 토큰 주인의 {@code userId} 로 범위가 잡히므로, 남의 예산은 애초에
 * 보이지 않고 "없음"과 같은 404 가 된다 (명세 0장 5)). 경로에는 연월만 있고 예산 ID 가
 * 없어서 ID 를 바꿔 찔러볼 수단도 없다.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    /**
     * BUDGET-01. 행이 없을 때 ERD 3장·서버_내부_처리 5장의 자동 복사를 수행한다.
     *
     * <ul>
     *   <li><b>이번 달</b>만 자동 생성한다. 과거 월은 만들지 않는다 — 지난 달 사용률이
     *       나중에 생긴 예산으로 바뀌면 안 되고, ANL-01 은 과거 월 예산 없음을
     *       {@code remainingBudget: null} 로 표현한다.</li>
     *   <li>원본은 "지난 달"이 아니라 <b>가장 최근 월</b>이다. 한동안 안 쓴 사용자는 지난 달
     *       행이 없다.</li>
     *   <li>복사할 원본조차 없으면(= 예산을 한 번도 설정하지 않음) 404 → 프론트가 초기 설정
     *       화면으로 보낸다.</li>
     * </ul>
     *
     * <p>조회인데 {@code readOnly} 가 아닌 이유가 이 INSERT 다. 같은 사용자가 이번 달
     * 첫 조회를 <b>동시에</b> 두 번 보내면 복합 UNIQUE {@code (USER_ID, YEAR_MONTH)} 에
     * 걸려 한쪽이 실패할 수 있다 — 새로고침 한 번이면 복사된 행이 보이므로 그대로 둔다.
     */
    @Transactional
    public BudgetResponse getBudget(Long userId, YearMonth yearMonth) {
        return findOrCopy(userId, yearMonth)
                .map(BudgetResponse::from)
                .orElseThrow(BusinessException::notFound);
    }

    /**
     * BUDGET-01 과 ANL-01 이 공유하는 조회 + 자동 복사. 규칙은 {@link #getBudget} 의 설명과
     * 같고, <b>없음을 예외가 아니라 빈 {@code Optional} 로</b> 돌려준다는 점만 다르다.
     *
     * <p>두 엔드포인트가 "예산 없음"을 다르게 표현하기 때문이다 — BUDGET-01 은 404(프론트가
     * 초기 설정 화면으로 보내는 신호)지만, ANL-01 은 같은 상태를 200 + {@code remainingBudget:
     * null} 로 내려야 한다(명세 ANL-01). 그래서 ANL-01 이 404 를 잡아서 삼키는 모양이 되지
     * 않도록 복사 규칙만 떼어 공유한다 — 규칙을 복제하면 "가장 최근 월"·"이번 달만 생성"이
     * 한쪽에서만 바뀐다.
     *
     * <p>엔티티를 그대로 내보내는 건 같은 도메인·같은 트랜잭션 안에서 읽기 전용으로 쓰기
     * 때문이다. 호출자는 금액만 꺼내 쓰고 변경하지 않는다.
     */
    @Transactional
    public Optional<Budget> findOrCopy(Long userId, YearMonth yearMonth) {
        Optional<Budget> saved = budgetRepository.findByUserIdAndYearMonth(userId, yearMonth.toString());
        if (saved.isPresent()) {
            return saved;
        }

        if (!yearMonth.equals(YearMonths.current())) {
            return Optional.empty();
        }

        return budgetRepository
                .findTopByUserIdAndYearMonthLessThanOrderByYearMonthDesc(userId, yearMonth.toString())
                .map(latest -> budgetRepository.save(latest.copyTo(yearMonth)));
    }

    /**
     * BUDGET-02. 없으면 생성, 있으면 덮어쓴다.
     *
     * <p>경로의 연월만 바뀌므로 마이페이지(이번 달)에서 저장해도 지난 달 행은 그대로다
     * (명세 BUDGET-02).
     */
    @Transactional
    public BudgetResponse saveBudget(Long userId, YearMonth yearMonth, SaveBudgetRequest request) {
        Budget budget = budgetRepository.findByUserIdAndYearMonth(userId, yearMonth.toString())
                .map(existing -> {
                    existing.overwrite(request.monthlyBudget(), request.savingsGoalOrZero(),
                            request.spendingFocus());
                    return existing;
                })
                .orElseGet(() -> budgetRepository.save(Budget.of(
                        findUserOrThrow(userId), yearMonth, request.monthlyBudget(),
                        request.savingsGoalOrZero(), request.spendingFocus())));

        return BudgetResponse.from(budget);
    }

    /** 토큰은 유효한데 사용자 행이 없다면 삭제된 계정이다 — {@code UserService} 와 같이 404. */
    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(BusinessException::notFound);
    }
}

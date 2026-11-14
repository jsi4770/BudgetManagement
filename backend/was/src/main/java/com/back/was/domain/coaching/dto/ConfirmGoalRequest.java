package com.back.was.domain.coaching.dto;

import com.back.was.domain.coaching.GoalType;
import com.back.was.global.validation.NotPastDate;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

/**
 * COACH-04 요청 — <b>확정 시점의 최종 값</b>이다. 규칙은 명세 COACH-04 표를 그대로 옮겼다.
 *
 * <p><b>저장된 제안과 달라도 된다</b>(명세 COACH-04 · ERD 7장). 사용자가 ± 와 기간 버튼으로
 * 바꾼 값은 어디에도 저장되지 않고 여기서 한 번에 올라온다 — 그래서 서버는 제안 값과
 * 비교하지 않고, 확정 이후의 실적·판정 기준은 이 값으로 만든 {@code CHALLENGE} 다.
 *
 * <p>검증이 두 군데로 나뉜다. <b>필드 하나로 판단되는 것은 여기</b>(필수·길이·최소값·지난
 * 날짜)에 애너테이션으로 두어 {@code details[{field, reason}]} 이 붙게 하고,
 * <b>필드 조합으로만 판단되는 것은 서비스</b>에 둔다 — {@code goalType} 과 목표 값의 짝
 * (ERD 9장 체크 조건은 {@code Challenge.of} 가 갖고 있다), {@code endDate} ≥
 * {@code startDate}, 그리고 기간이 7·14·30일 중 하나인지. 조합 규칙을 애너테이션으로
 * 옮기려면 명세에 없는 {@code reason} 값을 세 개 만들어야 해서, 그쪽은 메시지만 있는
 * 400 으로 답한다.
 *
 * @param categoryId 지출 카테고리. 없으면 404, 수입 카테고리면 400
 * @param title      최대 100자. 프론트의 기본값은 AI 제안 제목이고 없으면
 *                   {@code GoalTitle} 템플릿이다 — 서버는 받은 값을 그대로 쓴다
 * @param goalAmount {@code goalType == AMOUNT} 일 때 필수. {@code @Min} 은 {@code null} 을
 *                   통과시키므로 "필수"는 조합 검증이 맡는다
 * @param startDate  오늘 이후. 보통 확정한 날이다
 * @param endDate    {@code startDate} 이후, 기간은 7·14·30일 중 하나
 */
public record ConfirmGoalRequest(

        @NotNull
        Long categoryId,

        @NotBlank
        @Size(max = 100)
        String title,

        @NotNull
        GoalType goalType,

        @Min(1)
        Integer goalAmount,

        @Min(1)
        Integer goalCount,

        @NotNull
        @NotPastDate
        LocalDate startDate,

        @NotNull
        LocalDate endDate
) {
    /** 명세 COACH-04: "기간은 7·14·30일 중 선택". */
    private static final Set<Integer> ALLOWED_PERIOD_DAYS = Set.of(7, 14, 30);

    /**
     * 기간(일) — <b>양끝을 포함</b>한다. 명세의 예시가 그 정의다: 7일 = 11-04 ~ 11-10
     * ({@code endDate} = {@code startDate} + 기간 − 1).
     *
     * <p>{@code endDate} 가 {@code startDate} 보다 앞이면 0 이하가 되고, 그 경우는
     * {@link #hasAllowedPeriod()} 가 함께 걸러낸다 — 두 규칙을 한 계산으로 묶을 수 있는
     * 이유다.
     */
    public int periodDays() {
        return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
    }

    /** 기간이 7·14·30일 중 하나인지. 역순 날짜도 여기서 걸린다. */
    public boolean hasAllowedPeriod() {
        return ALLOWED_PERIOD_DAYS.contains(periodDays());
    }
}

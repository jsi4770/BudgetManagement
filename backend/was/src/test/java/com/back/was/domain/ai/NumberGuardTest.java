package com.back.was.domain.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 입력에 없던 숫자(환각)를 걸러내는 검증기. */
class NumberGuardTest {

    private static final Set<Double> ALLOWED = NumberGuard.allowed(
            101_000, 60_000, 41_000, new BigDecimal("68.33"), 3, 4);

    @Test
    @DisplayName("입력에 있던 금액·증가율은 그대로 통과한다")
    void knownNumbersPass() {
        assertThat(NumberGuard.unknownNumbers("이번 주 101,000원, 평소 60,000원보다 41,000원(68.33%) 더 썼어요.", ALLOWED))
                .isEmpty();
    }

    @Test
    @DisplayName("입력에 없던 금액은 걸러진다")
    void unknownAmountIsFlagged() {
        assertThat(NumberGuard.unknownNumbers("평소보다 55,000원 더 썼어요.", ALLOWED)).containsExactly("55,000");
    }

    @Test
    @DisplayName("31 이하 정수(날짜·횟수)와 날짜 표기는 허용한다")
    void smallIntegersAndDatesPass() {
        assertThat(NumberGuard.unknownNumbers("이번 주 3일 동안 4번, 2026-11-10 기준이에요.", ALLOWED)).isEmpty();
    }

    @Test
    @DisplayName("정수로 반올림한 증가율(68%)과 만 원 표기(10만 원)는 통과한다")
    void roundedAndManWonFormsPass() {
        assertThat(NumberGuard.unknownNumbers("평소보다 68% 늘었고 약 10만 원이에요.", ALLOWED)).isEmpty();
    }

    @Test
    @DisplayName("'4만 1,000원' 처럼 쪼개 쓴 금액은 합쳐서 41,000원으로 본다")
    void splitManWonIsJoined() {
        assertThat(NumberGuard.unknownNumbers("평소보다 4만 1,000원 더 썼어요.", ALLOWED)).isEmpty();
        assertThat(NumberGuard.unknownNumbers("평소보다 4만 2,000원 더 썼어요.", ALLOWED)).isNotEmpty();
    }

    @Test
    @DisplayName("대화에 이미 나온 숫자는 허용 집합에 더할 수 있다")
    void numbersInTextsCanBeAllowed() {
        Set<Double> allowed = NumberGuard.allowed();
        NumberGuard.addNumbersIn(List.of("이번 주 배달이 101,000원이에요"), allowed);

        assertThat(NumberGuard.unknownNumbers("101,000원이었죠.", allowed)).isEmpty();
        assertThat(NumberGuard.unknownNumbers("102,000원이었죠.", allowed)).isNotEmpty();
    }
}

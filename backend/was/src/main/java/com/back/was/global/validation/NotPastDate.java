package com.back.was.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 오늘(KST) 이상인 날짜만 허용한다 — 명세 COACH-04 의 "{@code startDate}: 오늘 이후".
 *
 * <p>{@link NotFutureDate} 의 거울상이고 이유도 같다: {@code @FutureOrPresent} 는 JVM 기본
 * 시간대로 판단하는데 이 서비스의 "오늘"은
 * {@link com.back.was.global.support.KstTime#today()} 하나여야 한다. 챌린지 시작일이 서버
 * 로케일에 따라 하루 흔들리면 기간 계산과 실적 집계가 같이 흔들린다.
 *
 * <p>애너테이션 이름이 곧 응답의 {@code details[].reason} 이다 —
 * {@link com.back.was.global.exception.GlobalExceptionHandler} 의 표가
 * {@code PAST_DATE} 로 옮긴다. <b>이름을 바꾸면 그 표도 같이 바꿔야 한다.</b>
 * {@code PAST_DATE} 는 명세가 정한 값이 아니라 {@code FUTURE_DATE} 와 짝을 맞춘 관례값이다
 * (명세 0장 4)가 확정한 {@code reason} 은 {@code REQUIRED} 와 {@code FUTURE_DATE} 둘뿐이다).
 *
 * <p>오늘은 <b>통과</b>다 — 명세 COACH-04 의 예시가 확정일 당일을 {@code startDate} 로
 * 쓴다({@code startDate} = 오늘, {@code endDate} = 오늘 + 기간 − 1).
 *
 * <p>{@code null} 은 통과시킨다 — 누락은 {@code @NotNull} 의 책임이고, 그래야 한 필드에
 * {@code REQUIRED} 와 {@code PAST_DATE} 가 동시에 달리지 않는다.
 */
@Documented
@Constraint(validatedBy = NotPastDateValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotPastDate {

    String message() default "지난 날짜는 입력할 수 없어요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

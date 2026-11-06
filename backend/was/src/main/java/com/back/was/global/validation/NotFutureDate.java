package com.back.was.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 오늘(KST) 이하인 날짜만 허용한다 — 명세 TX-01 / ERD 5장의 "미래 날짜 금지".
 *
 * <p>{@code @PastOrPresent} 를 쓰지 않는 이유는 그 기준이 JVM 기본 시간대라서다. 서비스는
 * KST 고정이므로 {@link com.back.was.global.support.KstTime#today()} 를 기준으로 삼는다.
 *
 * <p>애너테이션 이름이 곧 응답의 {@code details[].reason} 이다 — 명세 TX-01 이 요구하는
 * {@code FUTURE_DATE} 로 매핑하는 표가
 * {@link com.back.was.global.exception.GlobalExceptionHandler} 에 있다. <b>이름을 바꾸면
 * 그 표도 같이 바꿔야 한다.</b>
 *
 * <p>{@code null} 은 통과시킨다 — 누락은 {@code @NotNull} 의 책임이고, 그래야 한 필드에
 * {@code REQUIRED} 와 {@code FUTURE_DATE} 가 동시에 달리지 않는다.
 */
@Documented
@Constraint(validatedBy = NotFutureDateValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotFutureDate {

    String message() default "미래 날짜는 입력할 수 없어요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

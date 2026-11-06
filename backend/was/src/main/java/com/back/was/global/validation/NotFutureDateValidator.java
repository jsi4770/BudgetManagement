package com.back.was.global.validation;

import com.back.was.global.support.KstTime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

/** {@link NotFutureDate} 구현. 오늘(KST)은 포함, 내일부터 거부. */
public class NotFutureDateValidator implements ConstraintValidator<NotFutureDate, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        return value == null || !value.isAfter(KstTime.today());
    }
}

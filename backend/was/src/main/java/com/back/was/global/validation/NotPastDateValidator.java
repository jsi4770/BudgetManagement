package com.back.was.global.validation;

import com.back.was.global.support.KstTime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

/** {@link NotPastDate} 구현. 오늘(KST)은 포함, 어제까지 거부. */
public class NotPastDateValidator implements ConstraintValidator<NotPastDate, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        return value == null || !value.isBefore(KstTime.today());
    }
}

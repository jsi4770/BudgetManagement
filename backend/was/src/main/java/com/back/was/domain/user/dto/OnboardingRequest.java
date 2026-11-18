package com.back.was.domain.user.dto;

import com.back.was.domain.merchant.SupportedRegion;
import com.back.was.domain.user.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 가입 직후 온보딩(사는 지역·나이·성별). 세 값 모두 필수다 — 성별을 밝히고 싶지 않으면 {@code NONE}("선택 안 함")을
 * 보낸다. 지역은 지원 지역(천안·아산)만 받는다. 나이는 만 나이이고, 서버가 출생연도로 바꿔 저장한다
 * (나이를 그대로 저장하면 해가 바뀔 때 틀어진다).
 */
public record OnboardingRequest(

        @NotNull @Pattern(regexp = SupportedRegion.CODE_PATTERN)
        String regionCode,

        @NotNull @Min(14) @Max(99)
        Integer age,

        @NotNull
        Gender gender
) {
}

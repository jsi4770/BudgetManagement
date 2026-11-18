package com.back.was.domain.merchant;

import java.util.Arrays;
import java.util.Optional;

/**
 * 지역 가맹점 기능을 지원하는 시군. {@code code} 는 가맹점 API 의 {@code usage_rgn_cd}
 * (법정동코드 앞 5자리)와 같은 값이다.
 *
 * <p>충남 전체가 아니라 시연 대상인 천안·아산만 연다. 시군을 늘릴 때는 여기에 한 줄만 추가하면
 * 사용자 지역 검증·가맹점 동기화·목록 조회가 모두 따라온다.
 */
public enum SupportedRegion {

    CHEONAN("44130", "천안시"),
    ASAN("44200", "아산시");

    private final String code;
    private final String displayName;

    SupportedRegion(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<SupportedRegion> fromCode(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst();
    }

    /** {@code @Pattern} 에 쓰는 정규식 — 지원 지역 코드만 통과시킨다. */
    public static final String CODE_PATTERN = "^(44130|44200)$";
}

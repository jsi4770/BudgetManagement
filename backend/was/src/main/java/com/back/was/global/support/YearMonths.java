package com.back.was.global.support;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;

import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * 명세 0장 2) 연월(`YYYY-MM`) 경로 변수 처리.
 *
 * <p>{@code YearMonth} 를 컨트롤러 파라미터 타입으로 바로 쓰지 않는 이유: 변환이 실패하면
 * {@code MethodArgumentTypeMismatchException} 이 나고, 전역 핸들러는 그걸 "어느 필드인지
 * 신뢰할 수 없다"고 보아 {@code details} 없이 400 으로 내린다. 여기서 직접 파싱하면 어떤
 * 값이 왜 틀렸는지 메시지에 담을 수 있다.
 */
public final class YearMonths {

    /** {@code YearMonth.parse} 는 {@code 2026-11-04} 나 {@code +2026-11} 도 받으므로 형식을 먼저 좁힌다. */
    private static final Pattern FORMAT = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private YearMonths() {
    }

    /** @throws BusinessException 400 {@code VALIDATION_ERROR} — 형식이 {@code YYYY-MM} 이 아닐 때 */
    public static YearMonth parse(String raw) {
        if (raw == null || !FORMAT.matcher(raw).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "연월은 YYYY-MM 형식이어야 해요.");
        }
        return YearMonth.parse(raw);
    }

    /** ERD 0장: 서버·DB·스케줄러 모두 KST — "이번 달"의 기준도 KST 다. */
    public static YearMonth current() {
        return YearMonth.now(KstTime.ZONE);
    }
}

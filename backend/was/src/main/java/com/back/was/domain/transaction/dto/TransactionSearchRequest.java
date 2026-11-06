package com.back.was.domain.transaction.dto;

import com.back.was.domain.category.CategoryType;
import com.back.was.global.support.KstTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * TX-02 쿼리 파라미터. 규칙은 명세 TX-02 표를 그대로 옮겼다.
 *
 * <p>컨트롤러 파라미터를 하나씩 받지 않고 레코드로 묶은 이유: 제약을 메서드 파라미터에
 * 직접 걸면 Spring 7 의 내장 메서드 검증이 {@code HandlerMethodValidationException} 을
 * 던지는데 {@code GlobalExceptionHandler} 에 그 매핑이 없어 500 이 된다. {@code @Valid}
 * 가 붙은 {@code @ModelAttribute} 로 받으면 {@code BindException} 이 나서 기존 매핑대로
 * 400 {@code VALIDATION_ERROR} + {@code details} 가 그대로 성립한다.
 *
 * <p>모든 필드가 선택이라 기본값을 생성자에서 채우지 않고 {@code ...OrDefault()} 로
 * 노출한다 — {@code null} 은 "안 보냄"이고, 기본값은 명세 표의 값이다.
 */
public record TransactionSearchRequest(

        /** 기본값: 이번 달 1일 (KST). */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        /** 기본값: 오늘 (KST). */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        /** {@code null} 이면 수입·지출 전체. 거래에는 TYPE 이 없어 카테고리 TYPE 으로 걸러진다. */
        CategoryType type,

        Long categoryId,

        @Min(0)
        Integer page,

        /**
         * 명세의 "최대 100" 은 조용히 깎지 않고 400 으로 거부한다. 요청한 개수와 받은
         * 개수가 다르면 프론트의 페이지 계산이 어긋나고, 그 불일치는 응답만 봐서는
         * 드러나지 않는다.
         */
        @Min(1)
        @Max(100)
        Integer size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public LocalDate fromOrDefault() {
        return from != null ? from : KstTime.today().withDayOfMonth(1);
    }

    public LocalDate toOrDefault() {
        return to != null ? to : KstTime.today();
    }

    public int pageOrDefault() {
        return page != null ? page : DEFAULT_PAGE;
    }

    public int sizeOrDefault() {
        return size != null ? size : DEFAULT_SIZE;
    }
}

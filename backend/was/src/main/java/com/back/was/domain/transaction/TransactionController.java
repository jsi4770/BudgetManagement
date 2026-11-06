package com.back.was.domain.transaction;

import com.back.was.domain.transaction.dto.CalendarResponse;
import com.back.was.domain.transaction.dto.CreateTransactionRequest;
import com.back.was.domain.transaction.dto.ParseTransactionRequest;
import com.back.was.domain.transaction.dto.ParsedTransactionResponse;
import com.back.was.domain.transaction.dto.TransactionListItem;
import com.back.was.domain.transaction.dto.TransactionResponse;
import com.back.was.domain.transaction.dto.TransactionSearchRequest;
import com.back.was.domain.transaction.dto.UpdateTransactionRequest;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.response.PageResponse;
import com.back.was.global.security.AuthUser;
import com.back.was.global.support.YearMonths;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 명세 6장 거래(TRANSACTION). */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /** TX-01 거래 등록 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                   @RequestBody @Valid CreateTransactionRequest request) {
        return ApiResponse.ok(transactionService.create(authUser.id(), request));
    }

    /**
     * TX-07 자연어 분석. 저장하지 않고 추출 결과만 준다 — 프론트가 확인·수정 화면을
     * 띄우고 최종 저장은 TX-01 로 한다.
     *
     * <p>{@code /parse} 는 리터럴이라 TX-04 의 {@code /{id}} 와 겹치지 않는다. 메서드도
     * {@code POST} 라 더더욱 충돌하지 않는다.
     */
    @PostMapping("/parse")
    public ApiResponse<ParsedTransactionResponse> parse(@RequestBody @Valid ParseTransactionRequest request) {
        return ApiResponse.ok(transactionService.parse(request));
    }

    /**
     * TX-02 거래 목록. 페이지 정보는 명세 0장 3)대로 {@code data} 안
     * ({@code PageResponse})에 들어간다.
     */
    @GetMapping
    public ApiResponse<PageResponse<TransactionListItem>> search(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @ModelAttribute TransactionSearchRequest request) {
        return ApiResponse.ok(transactionService.search(authUser.id(), request));
    }

    /**
     * TX-03 달력용 일별 합계.
     *
     * <p>{@code yearMonth} 는 필수다. 빠지면 {@code MissingServletRequestParameterException}
     * 이 나고 전역 핸들러가 400 {@code VALIDATION_ERROR} +
     * {@code details[{field: yearMonth, reason: REQUIRED}]} 로 바꿔 준다.
     *
     * <p>이 경로는 TX-04 {@code GET /transactions/{id}} 보다 구체적인 리터럴이라 Spring 이
     * 먼저 매칭한다 — TX-04 가 생겨도 {@code calendar} 가 ID 로 해석되지 않는다.
     */
    @GetMapping("/calendar")
    public ApiResponse<CalendarResponse> getCalendar(@AuthenticationPrincipal AuthUser authUser,
                                                     @RequestParam String yearMonth) {
        return ApiResponse.ok(
                transactionService.getCalendar(authUser.id(), YearMonths.parse(yearMonth)));
    }

    /**
     * TX-04 거래 상세. 응답 모양은 TX-01 과 같다.
     *
     * <p>이 매핑은 위의 {@code /calendar} 보다 덜 구체적이므로 Spring 이 리터럴 경로를
     * 먼저 고른다 — 순서를 바꿔도 결과는 같지만, {@code calendar} 가 ID 로 들어오는 일은
     * 없다.
     */
    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> getTransaction(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(transactionService.getTransaction(authUser.id(), id));
    }

    /** TX-05 거래 수정 — 보낸 필드만 바꾼다. */
    @PatchMapping("/{id}")
    public ApiResponse<TransactionResponse> update(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long id,
                                                   @RequestBody @Valid UpdateTransactionRequest request) {
        return ApiResponse.ok(transactionService.update(authUser.id(), id, request));
    }

    /**
     * TX-06 거래 삭제.
     *
     * <p>{@code 204} 는 바디가 없으므로 {@code ApiResponse} 를 쓰지 않는다 — AUTH-04 와
     * 같은 이유다(명세 0장 3)).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        transactionService.delete(authUser.id(), id);
    }
}

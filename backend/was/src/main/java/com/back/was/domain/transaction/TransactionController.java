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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "6. 거래 (TX)", description = """
        수입·지출 기록. **수입/지출 구분 필드가 없습니다** — 방향은 `categoryId` 가 가리키는
        카테고리의 `type` 이 정하고 `amount` 는 항상 양수입니다.

        등록·수정·삭제는 같은 트랜잭션에서 **챌린지 실적 재계산과 넛지 재판정**을 함께 돌립니다.""")
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "TX-01 거래 등록", description = """
            저장 후 같은 트랜잭션에서 진행 중 챌린지의 실적과 이번 주 넛지를 다시 계산합니다.
            그래서 등록 직후 홈·챌린지 화면의 숫자가 이미 맞습니다.

            `memo` 는 선택입니다. **미래 날짜는 거부합니다**(오늘은 허용).

            **실패**: `400 VALIDATION_ERROR` — 미래 날짜는 `details[].reason: FUTURE_DATE` 로 구분됩니다 /
            `404 NOT_FOUND`(없는 카테고리)""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                   @RequestBody @Valid CreateTransactionRequest request) {
        return ApiResponse.ok(transactionService.create(authUser.id(), request));
    }

    /**
     * {@code /parse} 는 리터럴이라 TX-04 의 {@code /{id}} 와 겹치지 않는다. 메서드도
     * {@code POST} 라 더더욱 충돌하지 않는다.
     */
    @Operation(summary = "TX-07 자연어 분석", description = """
            "어제 배달 18000원" 같은 한 줄을 금액·날짜·카테고리·메모로 정리합니다.
            **저장하지 않습니다** — 프론트가 확인·수정 화면을 띄우고 최종 저장은 TX-01 로 합니다.

            `missingFields` 는 추출하지 못한 **필수** 필드 목록입니다(`amount`·`transactionDate`·`category`).
            일부만 뽑힌 것은 실패가 아니라 정상 응답입니다. `memo` 는 선택이라 목록에 넣지 않습니다.

            미래 날짜가 나와도 그대로 돌려줍니다 — 거부는 저장 시점(TX-01)의 몫이라 사용자가 고칠 기회를 얻습니다.

            > ⚠️ **현재 Gemini 어댑터가 없어 항상 `503 AI_UNAVAILABLE` 입니다.**
            > 프론트는 이 코드를 받으면 직접 입력 화면으로 안내하세요.

            **실패**: `400 VALIDATION_ERROR`, `422 AI_PARSE_FAILED`(필수 세 개를 모두 못 뽑음),
            `503 AI_UNAVAILABLE`""")
    @PostMapping("/parse")
    public ApiResponse<ParsedTransactionResponse> parse(@RequestBody @Valid ParseTransactionRequest request) {
        return ApiResponse.ok(transactionService.parse(request));
    }

    @Operation(summary = "TX-02 거래 목록", description = """
            페이지 정보는 `data` 안에 들어갑니다(`page`·`size`·`totalElements`·`totalPages`).

            정렬은 고정입니다: `transactionDate` 내림차순 → 같은 날짜는 등록 시각 내림차순.
            목록 항목에는 `createdAt` 이 없지만 정렬은 그 값을 씁니다.

            `from`·`to`·`type`·`categoryId` 는 모두 선택입니다. `from` 이 `to` 보다 뒤면
            에러가 아니라 빈 목록입니다.

            **실패**: `400 VALIDATION_ERROR` — `size` 는 100 이 상한이고, 넘기면 잘라주지 않고 거부합니다""")
    @GetMapping
    public ApiResponse<PageResponse<TransactionListItem>> search(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @ModelAttribute TransactionSearchRequest request) {
        return ApiResponse.ok(transactionService.search(authUser.id(), request));
    }

    /**
     * {@code yearMonth} 에 제약 애너테이션을 붙이지 않는다 — 빠지면
     * {@code MissingServletRequestParameterException} 이 나고 전역 핸들러가 이미 400
     * {@code VALIDATION_ERROR} + {@code reason: REQUIRED} 로 바꿔 준다.
     *
     * <p>이 경로는 TX-04 {@code /{id}} 보다 구체적인 리터럴이라 Spring 이 먼저 매칭한다 —
     * {@code calendar} 가 ID 로 해석되지 않는다.
     */
    @Operation(summary = "TX-03 달력용 일별 합계", description = """
            `yearMonth` 는 **필수**입니다(`YYYY-MM`).

            **거래가 있는 날짜만** 내려갑니다 — 0원인 날은 아예 빠집니다(달력에 점을 찍지 않으면 되니까).
            ANL-02 의 주간 막대와 반대 규칙입니다.

            **실패**: `400 VALIDATION_ERROR` — `yearMonth` 누락 시 `details[].reason: REQUIRED`""")
    @GetMapping("/calendar")
    public ApiResponse<CalendarResponse> getCalendar(@AuthenticationPrincipal AuthUser authUser,
                                                     @RequestParam String yearMonth) {
        return ApiResponse.ok(
                transactionService.getCalendar(authUser.id(), YearMonths.parse(yearMonth)));
    }

    /**
     * 위의 {@code /calendar} 보다 덜 구체적이므로 Spring 이 리터럴 경로를 먼저 고른다 —
     * 매핑 순서를 바꿔도 {@code calendar} 가 ID 로 들어오는 일은 없다.
     */
    @Operation(summary = "TX-04 거래 상세", description = """
            응답은 TX-01 과 같은 형식입니다.

            **실패**: `404 NOT_FOUND` — 없는 거래와 남의 거래가 바이트 단위로 같은 404 입니다""")
    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> getTransaction(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(transactionService.getTransaction(authUser.id(), id));
    }

    @Operation(summary = "TX-05 거래 수정", description = """
            **보낸 필드만 바꿉니다.** 바꿀 수 있는 것은 TX-01 의 네 필드 전부입니다.

            메모를 지우려면 `""`(빈 문자열)을 보내세요 — `null` 은 "그대로 두기"입니다.

            카테고리를 바꾸면 수입↔지출 방향이 바뀔 수 있고, 그때 **이전·새 카테고리 양쪽**의
            넛지와 챌린지가 다시 계산됩니다. 날짜를 챌린지 기간 밖으로 옮기면 그 실적에서 빠집니다.

            **실패**: `400 VALIDATION_ERROR`(미래 날짜 포함), `404 NOT_FOUND`""")
    @PatchMapping("/{id}")
    public ApiResponse<TransactionResponse> update(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long id,
                                                   @RequestBody @Valid UpdateTransactionRequest request) {
        return ApiResponse.ok(transactionService.update(authUser.id(), id, request));
    }

    /**
     * {@code 204} 는 바디가 없으므로 {@code ApiResponse} 를 쓰지 않는다 — AUTH-04 와
     * 같은 이유다(명세 0장 3)).
     */
    @Operation(summary = "TX-06 거래 삭제", description = """
            본문 없는 `204` 입니다. 삭제 후 챌린지 실적과 넛지를 다시 계산하므로
            조건에 못 미치게 된 미확인 넛지는 사라집니다.

            **멱등하지 않습니다** — 이미 지운 ID 를 다시 지우면 404 입니다. 204 로 답하면
            "그 ID 가 있었다"를 알려주는 셈이라 그렇게 두지 않았습니다.

            **실패**: `404 NOT_FOUND`""")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        transactionService.delete(authUser.id(), id);
    }
}

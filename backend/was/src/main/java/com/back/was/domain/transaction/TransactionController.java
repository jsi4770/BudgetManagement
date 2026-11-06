package com.back.was.domain.transaction;

import com.back.was.domain.transaction.dto.CreateTransactionRequest;
import com.back.was.domain.transaction.dto.TransactionListItem;
import com.back.was.domain.transaction.dto.TransactionResponse;
import com.back.was.domain.transaction.dto.TransactionSearchRequest;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.response.PageResponse;
import com.back.was.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
     * TX-02 거래 목록. 페이지 정보는 명세 0장 3)대로 {@code data} 안
     * ({@code PageResponse})에 들어간다.
     */
    @GetMapping
    public ApiResponse<PageResponse<TransactionListItem>> search(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @ModelAttribute TransactionSearchRequest request) {
        return ApiResponse.ok(transactionService.search(authUser.id(), request));
    }
}

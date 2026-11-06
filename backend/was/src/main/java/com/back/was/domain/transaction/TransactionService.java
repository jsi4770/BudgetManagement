package com.back.was.domain.transaction;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.transaction.dto.CreateTransactionRequest;
import com.back.was.domain.transaction.dto.TransactionResponse;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TX-01 거래 등록. */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    /**
     * TX-01. 거래는 항상 토큰 주인의 것으로 저장된다 — 요청에 사용자 ID 가 없다.
     *
     * <p><b>서버_내부_처리 1장의 후처리 2·3번(진행 중 챌린지 실적 재계산, 이번 주 넛지
     * 판정)은 아직 없다.</b> CHALLENGE·NUDGE 테이블과 판정 로직이 구현되면 <i>이
     * 메서드 안, 커밋 전</i>에 들어가야 한다(같은 트랜잭션·동기). AI 문구 생성만 커밋 후
     * 비동기다. 지금 거래를 넣어도 넛지·챌린지가 반응하지 않는 건 그래서다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는 카테고리 (명세 TX-01 Errors)
     */
    @Transactional
    public TransactionResponse create(Long userId, CreateTransactionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(BusinessException::notFound);
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(BusinessException::notFound);

        Transaction transaction = transactionRepository.save(Transaction.of(
                user, category, request.amount(), request.transactionDate(), request.memo()));

        return TransactionResponse.from(transaction);
    }
}

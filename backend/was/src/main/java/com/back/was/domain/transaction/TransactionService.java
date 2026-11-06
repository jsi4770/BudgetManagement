package com.back.was.domain.transaction;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.transaction.dto.CalendarResponse;
import com.back.was.domain.transaction.dto.CreateTransactionRequest;
import com.back.was.domain.transaction.dto.TransactionListItem;
import com.back.was.domain.transaction.dto.TransactionResponse;
import com.back.was.domain.transaction.dto.TransactionSearchRequest;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

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

    /**
     * TX-04 거래 상세. 응답은 TX-01 과 같은 모양이다(명세 TX-04 "TX-01과 동일").
     *
     * <p>없는 거래와 남의 거래를 모두 404 로 내린다 — 명세 0장 5). 두 경우를 구분하면
     * ID 를 순서대로 바꿔 넣는 것만으로 다른 사용자의 거래 존재 여부를 알 수 있다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} (명세 TX-04 Errors)
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long userId, Long id) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::notFound);

        return TransactionResponse.from(transaction);
    }

    /**
     * TX-02. 쿼리에 항상 {@code userId} 가 들어가므로 남의 거래는 결과에 섞이지 않는다
     * (서버_내부_처리 공통 전제).
     *
     * <p>{@code from} 이 {@code to} 보다 뒤면 검증으로 막지 않고 빈 목록을 준다 — 명세
     * TX-02 에 그 조합에 대한 규칙이 없고, 날짜 범위가 비는 건 "해당 기간에 거래 없음"과
     * 같은 상태다.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionListItem> search(Long userId, TransactionSearchRequest request) {
        int page = request.pageOrDefault();
        int size = request.sizeOrDefault();

        Page<Transaction> found = transactionRepository.search(
                userId,
                request.fromOrDefault(),
                request.toOrDefault(),
                request.type(),
                request.categoryId(),
                PageRequest.of(page, size));

        List<TransactionListItem> items = found.getContent().stream()
                .map(TransactionListItem::from)
                .toList();

        return PageResponse.of(items, page, size, found.getTotalElements());
    }

    /**
     * TX-03 달력용 일별 합계. 범위는 해당 월의 1일부터 말일까지고, 집계는 DB 가 한다 —
     * 한 달치 거래를 다 읽어 와 애플리케이션에서 더하면 달력 한 번 넘길 때마다 수백 건을
     * 옮기게 된다.
     */
    @Transactional(readOnly = true)
    public CalendarResponse getCalendar(Long userId, YearMonth yearMonth) {
        List<DailyAmountSum> sums = transactionRepository.sumDailyAmounts(
                userId,
                yearMonth.atDay(1),
                yearMonth.atEndOfMonth(),
                CategoryType.INCOME,
                CategoryType.EXPENSE);

        return CalendarResponse.of(yearMonth.toString(), sums);
    }
}

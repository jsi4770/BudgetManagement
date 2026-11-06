package com.back.was.domain.transaction;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.domain.ai.TextParseCommand;
import com.back.was.domain.ai.TransactionTextParser;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.domain.transaction.dto.CalendarResponse;
import com.back.was.domain.transaction.dto.CreateTransactionRequest;
import com.back.was.domain.transaction.dto.ParseTransactionRequest;
import com.back.was.domain.transaction.dto.ParsedTransactionResponse;
import com.back.was.domain.transaction.dto.TransactionListItem;
import com.back.was.domain.transaction.dto.TransactionResponse;
import com.back.was.domain.transaction.dto.TransactionSearchRequest;
import com.back.was.domain.transaction.dto.UpdateTransactionRequest;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.back.was.global.response.PageResponse;
import com.back.was.global.support.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** TX-01 거래 등록. */
@Service
@RequiredArgsConstructor
public class TransactionService {

    /** TX-07 의 필수 추출 필드 수 — {@code amount} · {@code transactionDate} · {@code category}. */
    private static final int REQUIRED_PARSED_FIELD_COUNT = 3;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionTextParser transactionTextParser;

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
     * TX-05 거래 수정. 보낸 필드만 바꾸고, 응답은 TX-01 과 같은 모양이다.
     *
     * <p>조회를 {@code findByIdAndUserId} 로 하므로 남의 거래를 수정할 경로가 없다 —
     * 소유권 검사를 따로 하지 않는 이유다.
     *
     * <p><b>서버_내부_처리 1장의 후처리가 빠져 있다</b>(TX-01 과 같은 이유로 CHALLENGE ·
     * NUDGE 가 없다). TX-01 보다 까다로운 점은 <i>수정 전</i> 카테고리와 날짜가
     * 필요하다는 것이다 — 재계산 대상이 이전 조합과 새 조합 둘이고, 조건 미달이 된
     * {@code UNREAD} 넛지는 삭제해야 한다. 그러니 구현할 때는 아래 변경을 적용하기
     * <i>전에</i> 이전 값을 복사해 두고, 커밋 전 같은 트랜잭션에서 양쪽을 돌려야 한다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 거래, 또는 없는 카테고리
     */
    @Transactional
    public TransactionResponse update(Long userId, Long id, UpdateTransactionRequest request) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::notFound);

        // 유일하게 실패할 수 있는 변경(없는 카테고리 → 404)을 먼저 처리한다. 롤백이
        // 어차피 되살려 주지만, 그래야 영속성 컨텍스트에 절반만 바뀐 엔티티가 남지 않는다.
        if (request.hasCategoryId()) {
            transaction.changeCategory(categoryRepository.findById(request.categoryId())
                    .orElseThrow(BusinessException::notFound));
        }
        if (request.hasAmount()) {
            transaction.changeAmount(request.amount());
        }
        if (request.hasTransactionDate()) {
            transaction.changeTransactionDate(request.transactionDate());
        }
        if (request.hasMemo()) {
            transaction.changeMemo(request.memo());
        }

        return TransactionResponse.from(transaction);
    }

    /**
     * TX-07 자연어 분석. <b>저장하지 않는다</b> — 추출 결과만 돌려주고, 저장은 사용자가
     * 확인·수정한 뒤 TX-01 이 한다. 그래서 {@code userId} 를 받지 않는다(인증은 필요하되
     * 결과가 사용자별로 달라지지 않는다).
     *
     * <p>{@code missingFields} 는 모델이 보고한 목록이 아니라 <b>값이 비었는지</b>로
     * 만든다(필수 세 필드 고정 순서). 모델 응답에 값과 목록이 어긋난 경우를 계약으로
     * 흘려보내지 않기 위함이고, 명세의 정의("추출 실패한 필수 필드")와도 정확히 같다.
     * {@code memo} 는 선택이라 목록에 들어가지 않는다.
     *
     * <p><b>422 는 셋 다 비었을 때만</b> 낸다. 일부만 뽑힌 건 실패가 아니라
     * {@code missingFields} 로 표현하는 정상 응답이다(명세 TX-07 이 부분 추출을 전제한다).
     * 모델이 고른 카테고리가 DB 에 없으면 "기타"로 추측하지 않고 카테고리 추출 실패로
     * 처리한다 — 틀린 카테고리는 넛지·분석까지 오염시키는데, 사용자에게 한 번 묻는 건
     * 그보다 싸다.
     *
     * <p>미래 날짜가 나와도 그대로 통과시킨다(명세 TX-07). 거부는 저장 시점인 TX-01 의
     * 몫이고, 그래야 사용자가 날짜를 고칠 기회를 얻는다.
     *
     * @throws BusinessException 503 {@code AI_UNAVAILABLE} / 422 {@code AI_PARSE_FAILED}
     */
    @Transactional(readOnly = true)
    public ParsedTransactionResponse parse(ParseTransactionRequest request) {
        List<CategoryResponse> categories = categoryRepository.findAllByOrderByIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();

        ParsedTransaction parsed = transactionTextParser.parse(
                new TextParseCommand(request.text(), KstTime.today(), categories));

        CategoryResponse category = findCategory(categories, parsed.categoryId());

        List<String> missingFields = new ArrayList<>();
        if (parsed.amount() == null) {
            missingFields.add("amount");
        }
        if (parsed.transactionDate() == null) {
            missingFields.add("transactionDate");
        }
        if (category == null) {
            missingFields.add("category");
        }

        if (missingFields.size() == REQUIRED_PARSED_FIELD_COUNT) {
            throw new BusinessException(ErrorCode.AI_PARSE_FAILED);
        }

        return new ParsedTransactionResponse(
                parsed.amount(), parsed.transactionDate(), category, parsed.memo(),
                List.copyOf(missingFields));
    }

    /** 모델이 고른 ID 를 실제 카테고리로 되돌린다. 목록에 없으면 {@code null}. */
    private static CategoryResponse findCategory(List<CategoryResponse> categories, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.stream()
                .filter(category -> category.id().equals(categoryId))
                .findFirst()
                .orElse(null);
    }

    /**
     * TX-06 거래 삭제. 본문 없이 204 다(명세 TX-06).
     *
     * <p><b>멱등하지 않다.</b> 이미 지운 거래를 다시 지우면 404 다 — AUTH-04 로그아웃과
     * 다른 선택인데, 로그아웃은 명세가 에러를 정의하지 않고 쿠키를 어차피 치워야 했지만
     * 여기서 성공을 흉내내면 "그 ID 가 있었다"를 알려주는 것과 같다. 프론트는 명세 0장 5)
     * 대로 404 를 "찾을 수 없음" 하나로 처리하면 된다.
     *
     * <p>삭제 전에 엔티티를 조회하는 건 소유권 확인 때문만이 아니다 — 서버_내부_처리
     * 1장의 후처리(진행 중 챌린지 실적 재계산, 해당 카테고리 넛지 재판정)가 들어오면
     * <b>지워진 거래의 카테고리·날짜</b>가 필요하고, 그 값은 삭제 후에는 얻을 수 없다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 거래
     */
    @Transactional
    public void delete(Long userId, Long id) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::notFound);

        transactionRepository.delete(transaction);
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

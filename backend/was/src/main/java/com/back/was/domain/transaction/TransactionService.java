package com.back.was.domain.transaction;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.domain.ai.TextParseCommand;
import com.back.was.domain.ai.TransactionTextParser;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.domain.nudge.NudgeJudgement;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * TX-01~07 거래. 등록·수정·삭제는 서버_내부_처리 1장의 후처리를 <b>같은 트랜잭션에서
 * 순서대로</b> 함께 돈다: 2번(챌린지 실적) → 3번(넛지 판정). 5번(넛지 AI 문구)만 커밋 후
 * 비동기다.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    /** TX-07 의 필수 추출 필드 수 — {@code amount} · {@code transactionDate} · {@code category}. */
    private static final int REQUIRED_PARSED_FIELD_COUNT = 3;

    private final TransactionRepository transactionRepository;
    private final NudgeJudgement nudgeJudgement;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionTextParser transactionTextParser;

    /**
     * TX-01. 거래는 항상 토큰 주인의 것으로 저장된다 — 요청에 사용자 ID 가 없다.
     *
     * <p>저장 뒤 <b>같은 트랜잭션에서 동기로</b> 넛지를 재판정한다(서버_내부_처리 1장
     * 순서 3번). 커밋 전이어야 "거래는 저장됐는데 카드만 안 생긴" 상태가 생기지 않는다.
     *
     * <p>후처리는 명세의 순서대로 2번(챌린지 실적) → 3번(넛지 판정)이다. <b>5번(넛지 AI
     * 문구)</b>만 커밋 후 비동기라 여기 없다.
     *
     * <p><b>지금은 3번의 호출만 남아 있다</b> — 2번은 챌린지 도메인과 함께, 5번은 넛지
     * 도메인과 함께 인수인계되었다. 2번이 들어갈 자리는 주석으로 표시해 두었고, 순서가
     * 규칙이므로 되살릴 때 넛지 판정 **앞**이어야 한다
     * (docs/인수인계_넛지_이상치_리포트.md).
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

        // 후처리 2번(진행 중 챌린지 실적 재계산)이 이 자리, **넛지 판정 앞**에 들어간다 —
        // 서버_내부_처리 1장이 정한 순서다. 챌린지 도메인이 인수인계되어 지금은 비어 있다
        // (docs/인수인계_넛지_이상치_리포트.md).
        nudgeJudgement.judge(user, category);

        return TransactionResponse.from(transaction);
    }

    /**
     * TX-05 거래 수정. 보낸 필드만 바꾸고, 응답은 TX-01 과 같은 모양이다.
     *
     * <p>조회를 {@code findByIdAndUserId} 로 하므로 남의 거래를 수정할 경로가 없다 —
     * 소유권 검사를 따로 하지 않는 이유다.
     *
     * <p>후처리에서 TX-01 보다 까다로운 점은 <i>수정 전</i> 카테고리가 필요하다는 것이다 —
     * 재판정 대상이 이전 카테고리와 새 카테고리 <b>둘 다</b>이고(서버_내부_처리 1장),
     * 조건 미달이 된 {@code UNREAD} 넛지는 지워야 한다. 그래서 변경을 적용하기 <i>전에</i>
     * 이전 카테고리를 붙잡아 둔다. 수입 카테고리로 바꾸는 것도 허용되므로 둘 중 한쪽만
     * 판정 대상일 수 있는데, 그 판단은 {@link NudgeJudgement} 안에 있다.
     *
     * <p>거래 <b>날짜</b>를 바꾼 경우에도 판정은 그대로 돌아간다 — 판정 기간은 언제나
     * 이번 주이고, 지난 주 날짜로 옮긴 거래는 표본에 들어가 평균을 바꾼다. 그래서 날짜로
     * 거르지 않는다.
     *
     * <p>챌린지 실적(후처리 2번)에는 <b>수정 전 날짜</b>도 필요하다. 카테고리가 그대로여도
     * 날짜가 기간 밖으로 나가면 이전 기간의 합계가 바뀌므로, 수정 전 (카테고리, 날짜) 와
     * 수정 후 (카테고리, 날짜) 를 각각 한 번씩 재계산한다. 재계산은 기간 전체를 다시
     * 집계하는 멱등 연산이라 같은 챌린지가 두 번 걸려도 결과가 같다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 거래, 또는 없는 카테고리
     */
    @Transactional
    public TransactionResponse update(Long userId, Long id, UpdateTransactionRequest request) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::notFound);

        // 변경을 적용하기 전에 붙잡아 둔다 — 아래에서 바뀌면 되찾을 방법이 없다.
        // 넛지는 카테고리만 필요하다. 챌린지 실적(후처리 2번)은 **날짜까지** 필요하므로
        // (서버_내부_처리 2장) 그 도메인이 돌아오면 여기서
        // `LocalDate previousDate = transaction.getTransactionDate();` 를 함께 붙잡을 것.
        Category previousCategory = transaction.getCategory();

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

        User owner = transaction.getUser();

        // 후처리 2번은 이 자리에서 **수정 전 (카테고리, 날짜)** 와 **수정 후
        // (카테고리, 날짜)** 로 각각 한 번씩, 넛지 판정 앞에 불러야 한다
        // (서버_내부_처리 1장). 카테고리가 그대로여도 날짜가 챌린지 기간 밖으로 나가면
        // 이전 기간의 합계가 바뀌므로 **수정 전 날짜**가 필요하다 — 챌린지 도메인이
        // 돌아오면 그 값을 여기서 다시 붙잡아야 한다.
        // 카테고리가 그대로면 같은 값이 두 번 들어가는데, 판정이 ID 로 중복을 없앤다.
        nudgeJudgement.judge(owner, previousCategory, transaction.getCategory());

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
        return parseText(request.text());
    }

    /**
     * 텍스트 한 덩이를 거래 정보로 정리한다 — TX-07 과 <b>RCPT-01 이 공유하는 경로</b>다.
     *
     * <p>RCPT-01 은 OCR 이 뽑은 텍스트를 가지고 여기로 들어온다(명세 RCPT-01 "응답 형식만
     * TX-07과 같음", 서버_내부_처리 9장 3단계). <b>복사하지 않고 같은 메서드를 쓰는 것이
     * 중요하다</b> — {@code missingFields} 유도 규칙, 422 조건, 모르는 카테고리 처리가
     * 두 벌이 되면 같은 확인 화면에 서로 다른 계약이 내려간다.
     *
     * <p>{@code @Transactional(readOnly = true)} 가 위의 {@code parse} 에 걸려 있고 RCPT-01
     * 쪽은 자기 트랜잭션에서 부른다. 저장하는 것이 없어 어느 쪽이든 안전하다.
     *
     * @param text 분석할 텍스트. 사용자가 입력한 문장(TX-07)이거나 OCR 원문(RCPT-01)
     * @throws BusinessException 503 {@code AI_UNAVAILABLE} / 422 {@code AI_PARSE_FAILED}
     */
    @Transactional(readOnly = true)
    public ParsedTransactionResponse parseText(String text) {
        List<CategoryResponse> categories = categoryRepository.findAllByOrderByIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();

        ParsedTransaction parsed = transactionTextParser.parse(
                new TextParseCommand(text, KstTime.today(), categories));

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
     * <p>삭제 전에 엔티티를 조회하는 건 소유권 확인 때문만이 아니다 — 후처리에
     * <b>지워진 거래의 카테고리와 날짜</b>가 필요하고, 그 값은 삭제 후에는 얻을 수 없다.
     *
     * <p>후처리는 삭제 <i>후</i>에 부른다. 집계 쿼리가 돌기 전에 Hibernate 가 DELETE 를
     * flush 하므로(같은 테이블을 읽는 JPQL 이라 auto-flush 대상이다) 지워진 거래가 합계에
     * 남지 않는다 — 챌린지 실적과 넛지 표본 둘 다 그 덕을 본다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 거래
     */
    @Transactional
    public void delete(Long userId, Long id) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::notFound);
        User owner = transaction.getUser();
        Category category = transaction.getCategory();
        LocalDate date = transaction.getTransactionDate();

        transactionRepository.delete(transaction);

        // 후처리 2번이 이 자리, 넛지 판정 앞에 들어간다 — 삭제된 행의 카테고리·날짜가
        // 필요해서 지우기 **전에** 붙잡아 두는 것이다(지운 뒤에는 얻을 수 없다).
        nudgeJudgement.judge(owner, category);
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

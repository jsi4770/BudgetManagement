package com.back.was.domain.transaction;

import com.back.was.domain.category.Category;
import com.back.was.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERD 5장 TRANSACTION.
 *
 * <p><b>수입/지출 구분 컬럼이 없다.</b> 부호도 없다 — {@code AMOUNT} 는 항상 양수이고
 * 방향은 {@link Category#getType()} 으로만 판단한다(ERD 5장). 그래서 카테고리를 바꾸면
 * 수입이 지출로 바뀔 수 있다는 점이 TX-05 수정 시의 후처리 규칙(서버_내부_처리 1장)과
 * 직결된다.
 *
 * <p>{@code TRANSACTION_DATE} 는 날짜만 있는 값이라 {@code LocalDate} 다. 같은 날짜
 * 안에서의 순서는 {@code CREATED_AT} 이 정한다(ERD 5장 정렬 규칙).
 */
@Entity
@Table(
        name = "transactions",
        indexes = @Index(
                name = "idx_transactions_user_transaction_date",
                columnList = "user_id, transaction_date"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** ERD 5장 CHECK > 0. 부호 없는 절대값이다. */
    @Column(nullable = false)
    private int amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    /** 선택 입력. 미입력은 {@code null} 로 저장한다 (ERD 5장 기본값 NULL). */
    @Column(length = 255)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Transaction(User user, Category category, int amount, LocalDate transactionDate, String memo) {
        this.user = user;
        this.category = category;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.memo = memo;
    }

    /**
     * TX-01 거래 등록.
     *
     * <p>미래 날짜 검증은 여기서 하지 않는다 — 명세 TX-01 은 그 경우
     * {@code details[].reason = FUTURE_DATE} 를 요구하므로 요청 DTO 의
     * {@code @NotFutureDate} 가 담당한다(ERD 5장: DB CHECK 로는 불가능).
     */
    public static Transaction of(User user, Category category, int amount, LocalDate transactionDate, String memo) {
        return new Transaction(user, category, amount, transactionDate, blankToNull(memo));
    }

    /**
     * TX-05 카테고리 변경. 수입 카테고리로 바꾸면 거래의 방향도 뒤집힌다 — 명세가
     * 금지하지 않으므로 허용하고, 그래서 후처리는 이전·새 카테고리 양쪽을 봐야 한다
     * (서버_내부_처리 1장).
     */
    public void changeCategory(Category category) {
        this.category = category;
    }

    public void changeAmount(int amount) {
        this.amount = amount;
    }

    public void changeTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    /** 빈 문자열·공백을 넘기면 메모가 지워진다 — TX-05 의 메모 삭제 경로. */
    public void changeMemo(String memo) {
        this.memo = blankToNull(memo);
    }

    /** 빈 문자열·공백만인 메모는 "없음"과 같게 다룬다. */
    private static String blankToNull(String memo) {
        return memo == null || memo.isBlank() ? null : memo;
    }
}

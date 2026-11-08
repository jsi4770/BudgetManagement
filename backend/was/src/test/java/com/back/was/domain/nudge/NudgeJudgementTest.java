package com.back.was.domain.nudge;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.transaction.Transaction;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 넛지 판정 단위 테스트 (CLAUDE.md: 넛지 판정은 단위 테스트 필수).
 *
 * <p>기대값은 docs/시연_데이터.md 6-1 — 배달만 넛지가 생기고(평균 60,000 · Z 9.32 ·
 * +68.33%) 식비·카페·교통은 표준편차 0 으로 생략된다.
 *
 * <p>각 구간의 금액은 그 구간의 <b>월요일</b>에 몰아 넣는다. 월요일은 항상 오늘 이하이고,
 * 이번 주 월요일 ~ 오늘(= 분석 기간)과 직전 4주의 같은 구간에 정확히 들어간다.
 */
@SpringBootTest
@Transactional
class NudgeJudgementTest {

    private static final LocalDate TODAY = KstTime.today();
    private static final LocalDate THIS_MONDAY = Weeks.mondayOf(TODAY);

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;
    private static final long TRANSPORT = 4L;
    private static final long SALARY = 7L;

    /** 시연 6-1 의 배달 표본 (평균 60,000 · 표본표준편차 약 4,397). */
    private static final int[] DELIVERY_SAMPLES = {55_000, 62_000, 58_000, 65_000};

    @Autowired
    NudgeJudgement nudgeJudgement;

    @Autowired
    NudgeRepository nudgeRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager entityManager;

    private User user;

    @BeforeEach
    void createLongTimeMember() {
        user = userRepository.save(User.signUp("홍길동", "judge@email.com", "irrelevant-hash"));
        // 시연 1장과 같은 D − 150일. 가입일이 표본 첫 주 월요일보다 앞서야 판정이 돌아간다.
        backdateSignUp(TODAY.minusDays(150));
    }

    /** {@code created_at} 은 {@code @CreationTimestamp} + {@code updatable = false} 라 JPA 로는 못 바꾼다. */
    private void backdateSignUp(LocalDate signUpDate) {
        entityManager.flush();
        entityManager.createNativeQuery("update users set created_at = :at where id = :id")
                .setParameter("at", signUpDate.atStartOfDay())
                .setParameter("id", user.getId())
                .executeUpdate();
        entityManager.clear();
        user = userRepository.findById(user.getId()).orElseThrow();
    }

    private Category category(long id) {
        return categoryRepository.findById(id).orElseThrow();
    }

    private void given(long categoryId, int amount, LocalDate date) {
        given(user, categoryId, amount, date);
    }

    private void given(User owner, long categoryId, int amount, LocalDate date) {
        transactionRepository.save(Transaction.of(owner, category(categoryId), amount, date, null));
    }

    /** 직전 4주 표본을 각 주 월요일에 넣는다. */
    private void givenSamples(long categoryId, int... amounts) {
        for (int weeksAgo = 1; weeksAgo <= amounts.length; weeksAgo++) {
            given(categoryId, amounts[weeksAgo - 1], THIS_MONDAY.minusWeeks(weeksAgo));
        }
    }

    /** 그 카테고리의 기간 내 거래를 모두 지운다 — 금액이 평균 아래로 떨어지는 상황을 만든다. */
    private void deleteTransactions(long categoryId, LocalDate from, LocalDate to) {
        transactionRepository.deleteAll(transactionRepository
                .search(user.getId(), from, to, null, categoryId, PageRequest.of(0, 100))
                .getContent());
    }

    private void givenDeliverySpike() {
        givenSamples(DELIVERY, DELIVERY_SAMPLES);
        given(DELIVERY, 101_000, THIS_MONDAY);
    }

    @Nested
    @DisplayName("생성")
    class Creating {

        @Test
        @DisplayName("시연 6-1: 배달 101,000 vs 평균 60,000 → 카드 생성 (Z 9.32 · +68.33% · UNREAD)")
        void createsNudgeForDeliverySpike() {
            givenDeliverySpike();

            nudgeJudgement.judge(user, category(DELIVERY));

            assertThat(nudgeRepository.findAll()).singleElement().satisfies(nudge -> {
                assertThat(nudge.getCategory().getId()).isEqualTo(DELIVERY);
                assertThat(nudge.getAnalysisStartDate()).isEqualTo(THIS_MONDAY);
                assertThat(nudge.getAnalysisEndDate()).isEqualTo(TODAY);
                assertThat(nudge.getSpendingAmount()).isEqualTo(101_000);
                assertThat(nudge.getAvgAmount()).isEqualTo(60_000);
                assertThat(nudge.getZScore()).isEqualByComparingTo("9.32");
                assertThat(nudge.getGrowthRate()).isEqualByComparingTo("68.33");
                assertThat(nudge.getStatus()).isEqualTo(NudgeStatus.UNREAD);
                assertThat(nudge.getAiAnalysis()).isNull();
                assertThat(nudge.getAiRecommendation()).isNull();
            });
        }

        @Test
        @DisplayName("시연 6-1: 식비·카페·교통은 표준편차 0 이라 생략된다")
        void skipsFlatSamples() {
            given(FOOD, 9_000, THIS_MONDAY);
            givenSamples(FOOD, 17_500, 17_500, 17_500, 17_500);
            given(CAFE, 4_500, THIS_MONDAY);
            givenSamples(CAFE, 4_500, 4_500, 4_500, 4_500);
            given(TRANSPORT, 1_650, THIS_MONDAY);
            givenSamples(TRANSPORT, 9_300, 9_300, 9_300, 9_300);

            nudgeJudgement.judge(user, category(FOOD), category(CAFE), category(TRANSPORT));

            assertThat(nudgeRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Z 는 충분해도 평균 대비 차이가 30,000원 미만이면 만들지 않는다")
        void skipsSmallSpikes() {
            given(DELIVERY, 3_000, THIS_MONDAY);
            givenSamples(DELIVERY, 1_000, 1_000, 1_000, 2_000);

            nudgeJudgement.judge(user, category(DELIVERY));

            assertThat(nudgeRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("월세는 판정 대상이 아니다 (ERD 4장·6장)")
        void rentIsNeverJudged() {
            given(RentCategory.ID, 550_000, THIS_MONDAY);
            givenSamples(RentCategory.ID, 1_000, 2_000, 1_000, 2_000);

            nudgeJudgement.judge(user, category(RentCategory.ID));

            assertThat(nudgeRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("수입 카테고리는 판정 대상이 아니다 — '평소보다 많이 벌었어요' 카드는 없다")
        void incomeIsNeverJudged() {
            given(SALARY, 5_000_000, THIS_MONDAY);
            givenSamples(SALARY, 1_000, 2_000, 1_000, 2_000);

            nudgeJudgement.judge(user, category(SALARY));

            assertThat(nudgeRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("가입일이 표본 첫 주 월요일 이후면 생략한다 (ERD 6장 판정 생략 ①)")
        void skipsWhenMemberIsTooNew() {
            givenDeliverySpike();
            backdateSignUp(THIS_MONDAY.minusWeeks(4).plusDays(1));

            nudgeJudgement.judge(user, category(DELIVERY));

            assertThat(nudgeRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("같은 카테고리를 두 번 넘겨도 카드는 하나다 (TX-05 에서 카테고리가 안 바뀐 경우)")
        void deduplicatesTargets() {
            givenDeliverySpike();

            nudgeJudgement.judge(user, category(DELIVERY), category(DELIVERY));

            assertThat(nudgeRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("다른 사용자의 거래는 금액에도 표본에도 섞이지 않는다")
        void scopedByUser() {
            User other = userRepository.save(
                    User.signUp("이영희", "judge-other@email.com", "irrelevant-hash"));
            given(other, DELIVERY, 101_000, THIS_MONDAY);
            givenSamples(DELIVERY, DELIVERY_SAMPLES);

            nudgeJudgement.judge(user, category(DELIVERY));

            // 내 이번 주 배달은 0원 → 평균보다 적다 → 카드 없음
            assertThat(nudgeRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("갱신 (ERD 6장 upsert)")
    class Updating {

        @Test
        @DisplayName("같은 주에 다시 판정하면 새 행이 아니라 그 행이 갱신된다")
        void refreshesInPlace() {
            givenDeliverySpike();
            nudgeJudgement.judge(user, category(DELIVERY));
            Long id = nudgeRepository.findAll().getFirst().getId();

            given(DELIVERY, 20_000, TODAY);
            nudgeJudgement.judge(user, category(DELIVERY));

            assertThat(nudgeRepository.findAll()).singleElement().satisfies(nudge -> {
                assertThat(nudge.getId()).isEqualTo(id);
                assertThat(nudge.getSpendingAmount()).isEqualTo(121_000);
                assertThat(nudge.getAvgAmount()).isEqualTo(60_000);
                assertThat(nudge.getAnalysisEndDate()).isEqualTo(TODAY);
            });
        }

        @Test
        @DisplayName("갱신해도 status 는 유지된다 — 닫은 카드는 닫힌 채로 남는다")
        void keepsStatusOnRefresh() {
            givenDeliverySpike();
            nudgeJudgement.judge(user, category(DELIVERY));
            nudgeRepository.findAll().getFirst().changeStatus(NudgeStatus.DISMISSED);

            given(DELIVERY, 20_000, TODAY);
            nudgeJudgement.judge(user, category(DELIVERY));

            assertThat(nudgeRepository.findAll()).singleElement().satisfies(nudge -> {
                assertThat(nudge.getStatus()).isEqualTo(NudgeStatus.DISMISSED);
                assertThat(nudge.getSpendingAmount()).isEqualTo(121_000);
            });
        }

        @Test
        @DisplayName("AI 문구는 갱신해도 지우지 않는다 — 재생성 전까지 이전 문구를 보여 준다")
        void keepsAiTextOnRefresh() {
            givenDeliverySpike();
            nudgeJudgement.judge(user, category(DELIVERY));
            nudgeRepository.findAll().getFirst().fillAiText("이전 분석", "이전 제안");

            given(DELIVERY, 20_000, TODAY);
            nudgeJudgement.judge(user, category(DELIVERY));

            assertThat(nudgeRepository.findAll()).singleElement().satisfies(nudge -> {
                assertThat(nudge.getAiAnalysis()).isEqualTo("이전 분석");
                assertThat(nudge.getAiRecommendation()).isEqualTo("이전 제안");
            });
        }
    }

    @Nested
    @DisplayName("조건 미달로 떨어졌을 때 (ERD 6장)")
    class FallingBelowThreshold {

        @Test
        @DisplayName("UNREAD 카드는 삭제된다")
        void deletesUnread() {
            givenDeliverySpike();
            nudgeJudgement.judge(user, category(DELIVERY));
            assertThat(nudgeRepository.findAll()).hasSize(1);

            deleteTransactions(DELIVERY, THIS_MONDAY, TODAY);
            nudgeJudgement.judge(user, category(DELIVERY));

            assertThat(nudgeRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("이미 반응한 카드(DISMISSED)는 유지된다 — 서버가 이력을 치우지 않는다")
        void keepsDismissed() {
            givenDeliverySpike();
            nudgeJudgement.judge(user, category(DELIVERY));
            nudgeRepository.findAll().getFirst().changeStatus(NudgeStatus.DISMISSED);

            deleteTransactions(DELIVERY, THIS_MONDAY, TODAY);
            nudgeJudgement.judge(user, category(DELIVERY));

            assertThat(nudgeRepository.findAll()).singleElement()
                    .extracting(Nudge::getStatus)
                    .isEqualTo(NudgeStatus.DISMISSED);
        }

        @Test
        @DisplayName("표준편차가 0 이 되면 생략한다 — 기존 카드를 지우지도, 갱신하지도 않는다")
        void zeroDeviationLeavesEverythingAlone() {
            givenDeliverySpike();
            nudgeJudgement.judge(user, category(DELIVERY));
            Nudge created = nudgeRepository.findAll().getFirst();
            int amountBefore = created.getSpendingAmount();

            // 표본 네 주를 모두 같은 금액으로 만든다 → 표준편차 0 → 판정 불가
            deleteTransactions(DELIVERY, THIS_MONDAY.minusWeeks(4), THIS_MONDAY.minusDays(1));
            givenSamples(DELIVERY, 10_000, 10_000, 10_000, 10_000);

            nudgeJudgement.judge(user, category(DELIVERY));

            assertThat(nudgeRepository.findAll()).singleElement().satisfies(nudge -> {
                assertThat(nudge.getSpendingAmount()).isEqualTo(amountBefore);
                assertThat(nudge.getAvgAmount()).isEqualTo(60_000);
            });
        }
    }
}

package com.back.was.dev;

import com.back.was.category.CategoryProvider;
import com.back.was.common.ForbiddenException;
import com.back.was.common.TimeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * DEV-01 시연용 시드 데이터 (계획표 7.2-1, 8.3-2).
 *
 * - 시연 계정에만
 * - 17주(4개월+) 거래, 매월 25일 급여, 매월 1일 월세 (ANL-03 MONTH 는 직전 3개월 필요)
 * - 가입일 = 첫 거래 전날
 * - 평소 패턴은 요일별로 고정 → 다른 카테고리는 이상치가 안 나옴
 * - 이번 주(오늘)에만 배달 3건 추가 → 배달만 Z ≥ 2 AND +30,000원 초과로 이상치
 */
@Service
public class DevSeedService {

    private static final int WEEKS = 17;

    private final SeedDataPort port;
    private final CategoryProvider categories;

    public DevSeedService(SeedDataPort port, CategoryProvider categories) {
        this.port = port;
        this.categories = categories;
    }

    @Transactional
    public Map<String, Object> seed(Long userId) {
        if (!port.isDemoAccount(userId)) {
            throw new ForbiddenException("시연 계정에서만 사용할 수 있어요.");
        }
        Long food = id("식비"), delivery = id("배달"), cafe = id("카페"), transport = id("교통"),
                rent = id("월세"), salary = id("급여");

        LocalDate today = TimeUtil.today();
        LocalDate start = TimeUtil.mondayOf(today).minusWeeks(WEEKS);
        LocalDate signup = start.minusDays(1);

        port.deleteAllTransactions(userId);
        port.setSignupDate(userId, signup);

        int count = 0;
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekday = dow.getValue() <= 5;

            if (d.getDayOfMonth() == 25) { port.saveTransaction(userId, salary, 2_500_000, d, "월급"); count++; }
            if (d.getDayOfMonth() == 1)  { port.saveTransaction(userId, rent, 550_000, d, "월세"); count++; }

            if (weekday) {   // 평일 점심 9,000~11,000원
                port.saveTransaction(userId, food, 9_000 + (d.getDayOfMonth() % 3) * 1_000L, d, "점심");
                count++;
            }
            if (dow != DayOfWeek.SUNDAY) { port.saveTransaction(userId, cafe, 4_500, d, "메가커피"); count++; }
            if (dow == DayOfWeek.TUESDAY || dow == DayOfWeek.THURSDAY) {
                port.saveTransaction(userId, transport, 12_000, d, "택시"); count++;
            }
            if (dow == DayOfWeek.WEDNESDAY) { port.saveTransaction(userId, delivery, 21_000, d, "배민"); count++; }
            if (dow == DayOfWeek.SATURDAY)  { port.saveTransaction(userId, delivery, 23_000, d, "요기요"); count++; }
        }

        // 이번 주 배달 급증 (오늘 날짜로 3건, +93,000원)
        port.saveTransaction(userId, delivery, 34_000, today, "배민 치킨");
        port.saveTransaction(userId, delivery, 28_000, today, "요기요 떡볶이");
        port.saveTransaction(userId, delivery, 31_000, today, "쿠팡이츠 피자");
        count += 3;

        return Map.of("signupDate", signup.toString(), "from", start.toString(),
                "to", today.toString(), "transactions", count);
    }

    private Long id(String name) {
        return categories.findByName(name)
                .orElseThrow(() -> new IllegalStateException("카테고리 '" + name + "'가 없어요."))
                .id();
    }
}

package com.back.was.domain.merchant;

import java.util.List;
import java.util.Locale;

/**
 * 가맹점 API 의 표준산업분류코드({@code ksic_cd}, 3자리)와 상호로 {@link MerchantGroup} 을 정한다.
 *
 * <p><b>AI 를 쓰지 않는다.</b> 같은 입력이면 항상 같은 결과가 나와야 목록 필터와 코칭 추천이
 * 어긋나지 않는다.
 *
 * <p>카페는 코드만으로 가를 수 없다. KSIC 10차의 562(주점 및 비알코올 음료점업)에 술집과 카페가
 * 함께 들어 있고, API 는 3자리만 주기 때문이다. 그래서 562 와 561 은 상호 키워드로 한 번 더
 * 가른다. 키워드에 걸리지 않는 카페는 식당·주점으로 남는다 — 놓치는 쪽이 엉뚱한 곳을
 * 카페로 추천하는 쪽보다 낫다.
 */
public final class MerchantClassifier {

    private static final List<String> CAFE_KEYWORDS = List.of(
            "카페", "까페", "커피", "coffee", "cafe", "스타벅스", "이디야", "메가mgc", "컴포즈",
            "빽다방", "투썸", "할리스", "더벤티", "매머드", "폴바셋", "블루보틀", "엔제리너스",
            "파스쿠찌", "탐앤탐스", "카페베네");

    private MerchantClassifier() {
    }

    public static MerchantGroup classify(String ksicCode, String name) {
        String ksic = ksicCode == null ? "" : ksicCode.strip();
        String prefix = ksic.length() >= 3 ? ksic.substring(0, 3) : ksic;

        return switch (prefix) {
            case "561", "562" -> looksLikeCafe(name) ? MerchantGroup.CAFE : MerchantGroup.FOOD;
            case "471", "472" -> MerchantGroup.GROCERY;
            case "473", "451", "452", "453" -> MerchantGroup.TRANSPORT;
            case "474", "475", "476", "477", "478", "479" -> MerchantGroup.SHOPPING;
            case "961", "962", "969" -> MerchantGroup.LIFE;
            default -> byDivision(prefix);
        };
    }

    /** 3자리 코드로 정해지지 않은 것은 앞 두 자리(중분류)로 본다. */
    private static MerchantGroup byDivision(String prefix) {
        if (prefix.length() < 2) {
            return MerchantGroup.ETC;
        }
        return switch (prefix.substring(0, 2)) {
            case "49" -> MerchantGroup.TRANSPORT;   // 육상 운송 (택시 포함)
            case "86" -> MerchantGroup.HEALTH;
            case "85" -> MerchantGroup.EDU;
            case "90", "91" -> MerchantGroup.LEISURE;
            default -> MerchantGroup.ETC;
        };
    }

    private static boolean looksLikeCafe(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return CAFE_KEYWORDS.stream().anyMatch(lower::contains);
    }
}

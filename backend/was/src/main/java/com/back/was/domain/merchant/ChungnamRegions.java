package com.back.was.domain.merchant;

import java.util.List;

/**
 * 충남 15개 시·군 전체. 지역 선택 화면에 모두 보여 주되 <b>서비스를 여는 곳({@link SupportedRegion})만
 * {@code selectable = true}</b> 다 — 나머지는 읽기 전용으로 두고 확장 예정임을 보여 준다.
 */
public final class ChungnamRegions {

    public record Region(String code, String name, boolean selectable) {}

    private static final String[][] ALL = {
            {"44130", "천안시"}, {"44150", "공주시"}, {"44180", "보령시"}, {"44200", "아산시"},
            {"44210", "서산시"}, {"44230", "논산시"}, {"44250", "계룡시"}, {"44270", "당진시"},
            {"44710", "금산군"}, {"44760", "부여군"}, {"44770", "서천군"}, {"44790", "청양군"},
            {"44800", "홍성군"}, {"44810", "예산군"}, {"44825", "태안군"},
    };

    private ChungnamRegions() {
    }

    /** 선택 가능한 지역이 앞에 오고, 나머지는 그 뒤에 가나다순이다. */
    public static List<Region> all() {
        return java.util.Arrays.stream(ALL)
                .map(r -> new Region(r[0], r[1], SupportedRegion.fromCode(r[0]).isPresent()))
                .sorted(java.util.Comparator.comparing((Region r) -> !r.selectable()).thenComparing(Region::name))
                .toList();
    }
}

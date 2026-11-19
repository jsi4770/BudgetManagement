package com.back.was.domain.youthpolicy.dto;

import com.back.was.domain.youthpolicy.YouthPolicy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * 홈 롤링 카드 한 장. {@code dDay} 는 마감까지 남은 일수(오늘 마감 = 0, 상시 = {@code null}),
 * {@code ageText} 는 "만 19~34세" 같은 표시용 문자열이다. 긴 설명은 카드에 맞게 잘라 둔다.
 */
public record YouthPolicyResponse(
        Long id,
        String plcyNo,
        String name,
        String summary,
        String category,
        List<String> keywords,
        String support,
        String ageText,
        String applyPeriod,
        LocalDate applyEnd,
        Integer dDay,
        String applyUrl,
        String refUrl,
        String institution,
        boolean regionSpecific
) {
    public static YouthPolicyResponse of(YouthPolicy p, LocalDate today) {
        return new YouthPolicyResponse(
                p.getId(), p.getPlcyNo(), p.getName(), cut(p.getExplanation(), 120), p.getCategory(),
                p.getKeywords() == null ? List.of()
                        : Arrays.stream(p.getKeywords().split(",")).map(String::strip).filter(s -> !s.isEmpty()).limit(5).toList(),
                cut(p.getSupportContent(), 160), ageText(p), p.getApplyPeriod() == null ? "상시" : p.getApplyPeriod(),
                p.getApplyEnd(), p.getApplyEnd() == null ? null : (int) ChronoUnit.DAYS.between(today, p.getApplyEnd()),
                p.getApplyUrl(), p.getRefUrl(), p.getInstitution(), p.getZipCount() <= 3);
    }

    private static String ageText(YouthPolicy p) {
        if (p.getMinAge() == null && p.getMaxAge() == null) {
            return "연령 제한 없음";
        }
        if (p.getMinAge() == null) {
            return "만 " + p.getMaxAge() + "세 이하";
        }
        if (p.getMaxAge() == null) {
            return "만 " + p.getMinAge() + "세 이상";
        }
        return "만 " + p.getMinAge() + "~" + p.getMaxAge() + "세";
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}

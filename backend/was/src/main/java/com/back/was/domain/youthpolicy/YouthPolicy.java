package com.back.was.domain.youthpolicy;

import com.back.was.domain.youthpolicy.YouthPolicyApiClient.Policy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 온통청년 청년정책 사본. 홈 화면 롤링에 쓰려고 천안·아산이 대상인 정책만 적재한다.
 *
 * <p>{@code plcyNo} 가 고유 키다. 신청 마감일({@code applyEnd})은 신청기간 문자열("20260101 ~ 20261201")의
 * 뒷부분에서 뽑고, 없으면(상시) {@code null} 이다.
 */
@Entity
@Table(name = "youth_policies", indexes = @Index(name = "idx_youth_policies_apply_end", columnList = "apply_end"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YouthPolicy {

    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern RANGE_END = Pattern.compile("~\\s*(\\d{8})");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plcy_no", nullable = false, unique = true, length = 30)
    private String plcyNo;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String supportContent;

    @Column(columnDefinition = "TEXT")
    private String applyMethod;

    /** 원문 신청기간 문자열. 비어 있으면 상시. */
    @Column(name = "apply_period", length = 100)
    private String applyPeriod;

    @Column(name = "apply_end")
    private LocalDate applyEnd;

    @Column(name = "apply_url", length = 500)
    private String applyUrl;

    @Column(name = "ref_url", length = 500)
    private String refUrl;

    @Column(length = 200)
    private String institution;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    /** 대상 시군구 코드 목록(쉼표 구분, 앞뒤에 쉼표를 붙여 {@code ,44130,} 로 포함 검색한다). */
    @Column(name = "zip_codes", columnDefinition = "TEXT")
    private String zipCodes;

    /** 대상 시군구가 몇 곳인지 — 적을수록 지역 맞춤 정책이다. */
    @Column(name = "zip_count", nullable = false)
    private int zipCount;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    private YouthPolicy(String plcyNo) {
        this.plcyNo = plcyNo;
    }

    public static YouthPolicy create(String plcyNo) {
        return new YouthPolicy(plcyNo);
    }

    public void refresh(Policy p, LocalDateTime syncedAt) {
        this.name = cut(p.name(), 300);
        this.keywords = p.keywords();
        this.explanation = p.explanation();
        this.category = cut(p.category(), 100);
        this.supportContent = p.supportContent();
        this.applyMethod = p.applyMethod();
        this.applyPeriod = p.applyPeriod() == null || p.applyPeriod().isBlank() ? null : cut(p.applyPeriod().strip(), 100);
        this.applyEnd = parseEnd(p.applyPeriod(), p.bizEnd());
        this.applyUrl = cut(p.applyUrl(), 500);
        this.refUrl = cut(p.refUrl(), 500);
        this.institution = cut(p.institution(), 200);
        this.minAge = p.minAge();
        this.maxAge = p.maxAge();
        String zips = p.zipCodes() == null ? "" : p.zipCodes().replace(" ", "");
        this.zipCodes = "," + zips + ",";
        this.zipCount = zips.isEmpty() ? 0 : zips.split(",").length;
        this.syncedAt = syncedAt;
    }

    /** 신청기간 끝 → 없으면 사업기간 끝 → 없으면 상시({@code null}). */
    static LocalDate parseEnd(String applyPeriod, String bizEnd) {
        if (applyPeriod != null) {
            Matcher m = RANGE_END.matcher(applyPeriod);
            if (m.find()) {
                return date(m.group(1));
            }
        }
        return date(bizEnd);
    }

    private static LocalDate date(String ymd) {
        if (ymd == null || ymd.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(ymd.strip(), YMD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** 이 나이가 대상인가. 상·하한이 없으면 제한 없음이다. */
    public boolean fitsAge(int age) {
        return (minAge == null || age >= minAge) && (maxAge == null || age <= maxAge);
    }
}

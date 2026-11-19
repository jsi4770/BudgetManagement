package com.back.was.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 지역사랑상품권 가맹점 (한국조폐공사 통합 가맹점기본정보 API 를 적재한 사본).
 *
 * <p>API 를 요청마다 부르지 않고 테이블에 담아 두는 이유: 일일 호출 한도(개발계정 10,000건)가 있고,
 * 1,000건에 약 9초가 걸리며, API 는 가맹점명 <b>완전 일치</b> 검색만 지원해서 "이름으로 찾기"를
 * 만들 수 없다. 영업 중인 가맹점만 담는다 — 폐업·휴업·해지는 동기화 때 걸러진다.
 *
 * <p>{@code altText} 는 API 가 가맹점마다 주는 해시로, 동기화의 고유 키다. 사업자번호는 목록
 * 화면에 필요 없어서 저장하지 않는다.
 */
@Entity
@Table(
        name = "local_merchants",
        indexes = {
                @Index(name = "idx_local_merchants_region_group", columnList = "region_code, merchant_group"),
                @Index(name = "idx_local_merchants_region_name", columnList = "region_code, name")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalMerchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alt_text", nullable = false, length = 64, unique = true)
    private String altText;

    /** {@link SupportedRegion#code()} — 가맹점 API 의 {@code usage_rgn_cd}. */
    @Column(name = "region_code", nullable = false, length = 5)
    private String regionCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "emd_name", length = 30)
    private String emdName;

    @Column(length = 255)
    private String address;

    @Column(length = 20)
    private String phone;

    private Double lat;

    private Double lng;

    @Column(name = "ksic_code", length = 10)
    private String ksicCode;

    @Column(name = "ksic_name", length = 60)
    private String ksicName;

    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_group", nullable = false, length = 20)
    private MerchantGroup merchantGroup;

    /** API 의 기준일자({@code crtr_ymd}). 데이터가 얼마나 묵었는지 화면에 밝히는 데 쓴다. */
    @Column(name = "base_date")
    private LocalDate baseDate;

    /** 마지막으로 동기화에서 확인된 시각. 이번 동기화에서 안 보인 행(폐업 등)을 지우는 기준이다. */
    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    private LocalMerchant(String altText, String regionCode) {
        this.altText = altText;
        this.regionCode = regionCode;
    }

    public static LocalMerchant create(String altText, String regionCode) {
        return new LocalMerchant(altText, regionCode);
    }

    /** 동기화 때마다 최신 값으로 덮어쓴다. 신규·기존 행이 같은 경로를 탄다. */
    public void refresh(String name, String emdName, String address, String phone,
                        Double lat, Double lng, String ksicCode, String ksicName,
                        MerchantGroup merchantGroup, LocalDate baseDate, LocalDateTime syncedAt) {
        this.name = name;
        this.emdName = emdName;
        this.address = address;
        this.phone = phone;
        this.lat = lat;
        this.lng = lng;
        this.ksicCode = ksicCode;
        this.ksicName = ksicName;
        this.merchantGroup = merchantGroup;
        this.baseDate = baseDate;
        this.syncedAt = syncedAt;
    }
}

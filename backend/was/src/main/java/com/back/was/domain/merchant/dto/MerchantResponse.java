package com.back.was.domain.merchant.dto;

import com.back.was.domain.merchant.LocalMerchant;

import java.time.LocalDate;

/**
 * 지역 가맹점 한 건. {@code group} 은 필터에 쓰는 코드({@code FOOD} 등), {@code groupLabel} 은 화면에
 * 그대로 보여 줄 이름이다. {@code lat}·{@code lng} 는 API 가 주지 않은 가맹점에서 {@code null} 이다.
 */
public record MerchantResponse(
        Long id,
        String name,
        String address,
        String emdName,
        String phone,
        String group,
        String groupLabel,
        String ksicName,
        Double lat,
        Double lng,
        LocalDate baseDate
) {
    public static MerchantResponse from(LocalMerchant m) {
        return new MerchantResponse(
                m.getId(),
                m.getName(),
                m.getAddress(),
                m.getEmdName(),
                m.getPhone(),
                m.getMerchantGroup().name(),
                m.getMerchantGroup().label(),
                m.getKsicName(),
                m.getLat(),
                m.getLng(),
                m.getBaseDate());
    }
}

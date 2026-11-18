package com.back.was.domain.merchant.dto;

import com.back.was.domain.merchant.SupportedRegion;

public record RegionResponse(String code, String name) {

    public static RegionResponse from(SupportedRegion region) {
        return new RegionResponse(region.code(), region.displayName());
    }
}

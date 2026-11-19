package com.back.was.domain.dev.dto;

import com.back.was.domain.dev.DemoScenario;
import jakarta.validation.constraints.NotNull;

/**
 * DEV-01 요청 (명세 DEV-01).
 *
 * <p>{@code scenario} 는 <b>필수</b>다 — DEV-02 의 {@code scenario} 와 다른 점이다. 저쪽은
 * 비우면 "현재 기록 그대로"라는 의미가 있지만, 여기서는 둘 중 어느 데이터를 깔지가 요청의
 * 전부라서 기본값을 정할 근거가 없다. 마이페이지의 두 버튼이 각각 하나를 보낸다.
 */
public record SeedRequest(

        @NotNull
        DemoScenario scenario
) {
}

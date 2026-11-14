package com.back.was.domain.admin;

import com.back.was.domain.admin.dto.KpiResponse;
import org.springframework.stereotype.Service;

/**
 * ADMIN-01 KPI — <b>아직 구현되지 않았다.</b>
 *
 * <p>비어 있는 동안 {@link UnsupportedOperationException} 을 던져 500 {@code INTERNAL_ERROR}
 * 가 된다. 집계 쿼리({@code ChallengeRepository.countOutcomes})와 응답 계산
 * ({@link KpiResponse#from})은 이미 있으므로 둘을 잇기만 하면 된다. 역할 검사는 여기 두지
 * 않는다 — {@code SecurityConfig} 의 {@code /admin/**} 가 맡는다.
 *
 * <p>규칙은 {@code docs/AI_코칭_소비분석_인수인계.md} 1장 ⑦.
 */
@Service
public class AdminKpiService {

    /** ADMIN-01 KPI 조회 — 프로젝트 전체 집계라 사용자 ID 를 받지 않는다. */
    public KpiResponse getKpi() {
        throw new UnsupportedOperationException("ADMIN-01 은 아직 구현되지 않았어요.");
    }
}

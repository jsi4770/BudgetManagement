package com.back.was.domain.admin;

import com.back.was.domain.admin.dto.KpiResponse;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 명세 13장 관리자 API. 지금은 ADMIN-01 하나다. */
@Service
@RequiredArgsConstructor
public class AdminKpiService {

    private final ChallengeRepository challengeRepository;

    /**
     * ADMIN-01 KPI 조회 (ERD 13장).
     *
     * <p><b>사용자 ID 를 받지 않는다.</b> 프로젝트 전체의 지표라서 집계에 사용자 범위가
     * 없고(명세 ADMIN-01 "발표용 프로젝트 성공 지표"), 그래서 다른 챌린지 조회들과 달리
     * {@code COACHING.USER_ID} 경유 조인이 없다.
     *
     * <p>권한 검사가 여기 없는 것도 의도다. {@code SecurityConfig} 가 {@code /admin/**} 을
     * {@code hasRole("ADMIN")} 으로 막고 {@code ApiAccessDeniedHandler} 가 403
     * {@code FORBIDDEN} 을 쓴다 — 서비스에서 역할을 다시 보면 검사가 두 군데가 되고,
     * 소유권과 달리 관리자 권한은 URL 패턴으로 막는 것이 이 프로젝트의 규칙이다
     * (명세 0장 5): {@code FORBIDDEN} 은 관리자 API 권한 전용).
     *
     * <p>집계 결과가 비어 있어도(종료된 챌린지 0건) 200 이다 — 자세한 처리는
     * {@link KpiResponse#from}.
     */
    @Transactional(readOnly = true)
    public KpiResponse getKpi() {
        return KpiResponse.from(challengeRepository.countOutcomes(
                ChallengeStatus.SUCCESS, ChallengeStatus.FAILED));
    }
}

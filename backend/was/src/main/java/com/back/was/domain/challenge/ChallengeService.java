package com.back.was.domain.challenge;

import com.back.was.domain.challenge.dto.ChallengeResponse;
import com.back.was.domain.report.dto.GrowthReportResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 명세 11장 챌린지 · 성장 리포트 조회 — <b>아직 구현되지 않았다.</b>
 *
 * <p>비어 있는 동안 모든 메서드가 {@link UnsupportedOperationException} 을 던져 500
 * {@code INTERNAL_ERROR} 가 된다. 챌린지를 만드는 COACH-04, 실적을 갱신하는 거래 후처리
 * 2번, 종료 판정·성장 리포트를 만드는 00:05 스케줄러도 아직 없다.
 *
 * <p>규칙은 {@code docs/AI_코칭_소비분석_인수인계.md} 1장 ④⑤⑥, 원래 구현은
 * {@code git show backup/before-handover:./src/main/java/com/back/was/domain/challenge/ChallengeService.java}.
 */
@Service
public class ChallengeService {

    /** CHL-01 챌린지 목록. {@code status} 가 {@code null} 이면 전체. */
    public List<ChallengeResponse> getChallenges(Long userId, ChallengeStatus status) {
        throw new UnsupportedOperationException("CHL-01 은 아직 구현되지 않았어요.");
    }

    /** CHL-02 챌린지 상세. 없는/남의 챌린지는 404 {@code NOT_FOUND}. */
    public ChallengeResponse getChallenge(Long userId, Long id) {
        throw new UnsupportedOperationException("CHL-02 는 아직 구현되지 않았어요.");
    }

    /** CHL-03 성장 리포트. 없음·남의 것·진행 중이 모두 같은 404 다. */
    public GrowthReportResponse getReport(Long userId, Long challengeId) {
        throw new UnsupportedOperationException("CHL-03 은 아직 구현되지 않았어요.");
    }
}

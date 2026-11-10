package com.back.was.domain.challenge;

import com.back.was.domain.challenge.dto.ChallengeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 명세 11장 챌린지(CHALLENGE) 조회. 지금은 CHL-01 만 있다.
 *
 * <p><b>챌린지를 만드는 것은 COACH-04</b>, 실적을 갱신하는 것은 거래 후처리 2번
 * ({@link ChallengePerformance})이다. 여기는 읽기 전용이다 — 챌린지를 수정·삭제하거나
 * 중도 포기하는 API 는 명세에 없다(11장은 조회 세 개뿐).
 */
@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeRepository challengeRepository;

    /**
     * CHL-01 챌린지 목록.
     *
     * <p>응답은 페이지 정보 없는 <b>배열</b>이다(명세 CHL-01 예시) — CAT-01 과 같은 모양이고
     * 이유는 {@link ChallengeRepository#findAllByUserId} 에 적었다.
     *
     * <p>{@code achievementRate} 와 두 실적 값은 <b>저장된 값을 그대로</b> 내려보낸다. 조회
     * 시점에 다시 집계하지 않는 것이 의도다 — 후처리 2번이 거래가 바뀔 때마다 갱신하므로
     * 이미 최신이고, 여기서 또 계산하면 같은 숫자를 내는 코드가 두 벌이 된다(서버_내부_처리
     * 2장이 갱신 주체를 후처리로 못박았다). {@code daysLeft} 만 저장되지 않아 응답을 만들 때
     * 센다.
     *
     * <p>빈 목록은 정상이다 — 챌린지를 한 번도 확정하지 않은 사용자가 대부분이고, 404 로
     * 내리면 프론트의 "찾을 수 없음" 처리와 섞인다.
     *
     * @param status {@code null} 이면 전체 (명세 CHL-01 "없으면 전체"). 완료 목록은 프론트가
     *               전체를 받아 {@code SUCCESS}·{@code FAILED} 로 걸러 쓴다
     */
    @Transactional(readOnly = true)
    public List<ChallengeResponse> getChallenges(Long userId, ChallengeStatus status) {
        return challengeRepository.findAllByUserId(userId, status).stream()
                .map(ChallengeResponse::from)
                .toList();
    }
}

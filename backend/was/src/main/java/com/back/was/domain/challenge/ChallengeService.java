package com.back.was.domain.challenge;

import com.back.was.domain.challenge.dto.ChallengeResponse;
import com.back.was.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 명세 11장 챌린지(CHALLENGE) 조회 — CHL-01·02.
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

    /**
     * CHL-02 챌린지 상세. 응답은 <b>CHL-01 항목과 같은 형식</b>이고(명세 CHL-02) 배열이
     * 아닌 단일 객체다 — 그래서 {@link ChallengeResponse} 를 그대로 재사용한다.
     *
     * <p>CHL-01 과 담기는 값도 같다. 목록에서 고른 항목을 상세로 열었을 때 숫자가 달라지면
     * 설명할 수 없으므로, 두 경로 모두 <b>저장된 실적을 그대로</b> 내려보내고
     * {@code daysLeft} 만 응답을 만들 때 센다.
     *
     * <p>없는 챌린지와 남의 챌린지는 모두 404 다(명세 CHL-02 Errors · 0장 5)). 조건이
     * 쿼리에 들어 있어({@code findByIdAndCoachingUserId}) 두 경우의 응답이 구조적으로
     * 같다 — ID 를 순서대로 넣어 보는 것만으로 다른 사용자의 챌린지 존재 여부를 알 수 없다.
     *
     * <p><b>종료된 챌린지도 열린다.</b> 상태로 걸러내지 않는 이유는
     * {@link ChallengeRepository#findByIdAndCoachingUserId} 에 적었다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 챌린지
     */
    @Transactional(readOnly = true)
    public ChallengeResponse getChallenge(Long userId, Long id) {
        return challengeRepository.findByIdAndCoachingUserId(id, userId)
                .map(ChallengeResponse::from)
                .orElseThrow(BusinessException::notFound);
    }
}

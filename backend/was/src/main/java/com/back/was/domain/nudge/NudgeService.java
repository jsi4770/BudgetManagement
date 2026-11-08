package com.back.was.domain.nudge;

import com.back.was.domain.nudge.dto.NudgeDetailResponse;
import com.back.was.domain.nudge.dto.NudgeResponse;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 명세 9장 넛지(NUDGE) 조회.
 *
 * <p><b>넛지는 서버가 만든다</b>(명세 9장 머리말) — 프론트는 조회와 상태 변경만 한다.
 * 만드는 쪽은 거래 후처리의 판정(서버_내부_처리 3장)이고 <b>아직 구현되지 않았다</b>.
 * 그래서 지금 이 조회는 거래를 아무리 넣어도 빈 결과다. 판정이 들어오면 같은 테이블을
 * 읽으므로 이 코드는 바뀌지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NudgeService {

    private final NudgeRepository nudgeRepository;

    /**
     * NDG-01 홈 노출 넛지 1개. <b>없는 것이 정상</b>이라 404 가 아니고, 컨트롤러가
     * {@code data: null} 로 내려 프론트가 카드를 숨긴다.
     *
     * <p>"이번 주"의 기준은 KST 월요일이다({@link Weeks}) — 넛지의
     * {@code analysisStartDate} 가 그 값으로 저장되므로 등치 비교가 성립하고, 그래서 지난
     * 주 넛지는 미확인이어도 걸러진다(명세 NDG-01).
     *
     * <p>푸시·SSE 는 없다. 프론트가 홈 진입과 탭 재활성화 때 다시 호출하는 구조다
     * (명세 NDG-01 "조회 시점").
     */
    @Transactional(readOnly = true)
    public Optional<NudgeResponse> getCurrentNudge(Long userId) {
        return nudgeRepository
                .findTopByUserIdAndAnalysisStartDateAndStatusOrderByUpdatedAtDesc(
                        userId, Weeks.mondayOf(KstTime.today()), NudgeStatus.UNREAD)
                .map(NudgeResponse::from);
    }

    /**
     * NDG-02 넛지 상세. NDG-01 과 달리 <b>이번 주·{@code UNREAD} 로 제한하지 않는다</b> —
     * 홈에 뜨지 않는 카드도 링크로 열 수 있어야 하고, 명세 NDG-02 에 그런 조건이 없다.
     *
     * <p>없는 넛지와 남의 넛지가 모두 404 다(명세 0장 5)). 두 경우를 구분하면 ID 를
     * 순서대로 넣어 보는 것만으로 다른 사용자의 넛지 존재 여부를 알 수 있다.
     *
     * <p>AI 문구가 아직 비었으면 {@code null} 그대로 내려간다 — 그게 프론트의 "AI가
     * 분석 중이에요" 신호다(명세 NDG-02).
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 넛지
     */
    @Transactional(readOnly = true)
    public NudgeDetailResponse getNudge(Long userId, Long id) {
        return nudgeRepository.findByIdAndUserId(id, userId)
                .map(NudgeDetailResponse::from)
                .orElseThrow(BusinessException::notFound);
    }
}

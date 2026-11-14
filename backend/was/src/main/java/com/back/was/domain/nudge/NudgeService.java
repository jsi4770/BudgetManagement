package com.back.was.domain.nudge;

import com.back.was.domain.nudge.dto.ChangeNudgeStatusRequest;
import com.back.was.domain.nudge.dto.NudgeDetailResponse;
import com.back.was.domain.nudge.dto.NudgeReaction;
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
 * 만드는 쪽은 거래 후처리의 판정({@code NudgeJudgement}, 서버_내부_처리 3장)이다 —
 * 거래 등록·수정·삭제와 <b>같은 트랜잭션</b>에서 돌므로, 거래가 커밋된 뒤에 이 조회가
 * 카드를 못 보는 순간은 없다. AI 문구만 커밋 이후 비동기로 채워져서({@code NudgeTextFiller})
 * 잠깐 {@code null} 일 수 있다.
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

    /**
     * NDG-03 상태 변경. 응답은 NDG-02 와 같은 모양이다(명세 NDG-03).
     *
     * <p>조회가 {@code findByIdAndUserId} 라 남의 넛지를 바꿀 경로가 없다 — 소유권 검사를
     * 따로 하지 않는 이유다.
     *
     * <p><b>되돌리는 전이는 없다.</b> 요청으로 올 수 있는 값이 {@link NudgeReaction} 세 개로
     * 제한되므로 {@code UNREAD} 로 돌아갈 수 없고, 그래서 "닫은 넛지는 재노출되지 않음"
     * (명세 NDG-03)이 상태 기계 차원에서 보장된다. 같은 상태를 다시 보내는 건 허용한다 —
     * 명세에 에러가 없고, 실패로 만들면 프론트가 재시도조차 못 한다.
     *
     * <p>판정이 같은 주에 다시 돌아 금액이 갱신돼도 이 값은 유지된다(ERD 6장) — 판정 쪽이
     * 상태를 건드리지 않기 때문이다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 넛지
     */
    @Transactional
    public NudgeDetailResponse changeStatus(Long userId, Long id, ChangeNudgeStatusRequest request) {
        Nudge nudge = nudgeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::notFound);

        nudge.changeStatus(request.status().toStatus());

        return NudgeDetailResponse.from(nudge);
    }
}

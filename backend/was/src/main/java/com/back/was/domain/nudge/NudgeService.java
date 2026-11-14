package com.back.was.domain.nudge;

import com.back.was.domain.nudge.dto.ChangeNudgeStatusRequest;
import com.back.was.domain.nudge.dto.NudgeDetailResponse;
import com.back.was.domain.nudge.dto.NudgeResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 명세 9장 넛지(NUDGE) 조회 — <b>아직 구현되지 않았다.</b>
 *
 * <p>엔티티·리포지토리·컨트롤러·DTO 는 자리를 잡아 두었고, 메서드 본문만 비어 있다. 비어 있는
 * 동안에는 그럴듯한 가짜 데이터 대신 {@link UnsupportedOperationException} 을 던져
 * {@code GlobalExceptionHandler} 가 500 {@code INTERNAL_ERROR} 로 답한다.
 *
 * <p>규칙(노출 조건 세 개는 쿼리에, NDG-02 는 노출 조건 없음, NDG-03 은
 * {@code NudgeReaction} 으로만 전이)은 {@code docs/AI_코칭_소비분석_인수인계.md} 1장 ②,
 * 원래 구현은 {@code git show backup/before-handover:./src/main/java/com/back/was/domain/nudge/NudgeService.java}.
 */
@Service
public class NudgeService {

    /** NDG-01 홈 노출 넛지 1개. 없으면 {@link Optional#empty()} — 컨트롤러가 {@code data: null} 로 내린다. */
    public Optional<NudgeResponse> getCurrentNudge(Long userId) {
        throw new UnsupportedOperationException("NDG-01 은 아직 구현되지 않았어요.");
    }

    /** NDG-02 넛지 상세. 없는/남의 넛지는 404 {@code NOT_FOUND}. */
    public NudgeDetailResponse getNudge(Long userId, Long id) {
        throw new UnsupportedOperationException("NDG-02 는 아직 구현되지 않았어요.");
    }

    /** NDG-03 상태 변경. 응답은 NDG-02 와 같은 모양이다. */
    public NudgeDetailResponse changeStatus(Long userId, Long id, ChangeNudgeStatusRequest request) {
        throw new UnsupportedOperationException("NDG-03 은 아직 구현되지 않았어요.");
    }
}

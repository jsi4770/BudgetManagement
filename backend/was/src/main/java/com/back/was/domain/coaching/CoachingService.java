package com.back.was.domain.coaching;

import com.back.was.domain.ai.CoachingOpeningCommand;
import com.back.was.domain.ai.CoachingOpeningGenerator;
import com.back.was.domain.coaching.dto.CoachingResponse;
import com.back.was.domain.coaching.dto.StartCoachingRequest;
import com.back.was.domain.nudge.Nudge;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.nudge.NudgeStatus;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 명세 10장 AI 코칭(COACHING). 지금은 COACH-01 만 있다. */
@Service
@RequiredArgsConstructor
public class CoachingService {

    private final CoachingRepository coachingRepository;
    private final CoachingMessageRepository coachingMessageRepository;
    private final NudgeRepository nudgeRepository;
    private final UserRepository userRepository;
    private final CoachingOpeningGenerator coachingOpeningGenerator;

    /**
     * COACH-01 코칭 시작. 세션과 <b>AI 첫 메시지를 한 트랜잭션에서 함께</b> 저장한다
     * (서버_내부_처리 6장) — ERD 가 COACHING : COACHING_MESSAGE 를 1 : 1..N 으로 둔 것이
     * 그 뜻이다. 메시지 없는 세션은 유효한 상태가 아니다.
     *
     * <p>순서가 규칙이다:
     *
     * <ol>
     *   <li><b>넛지를 먼저 찾는다.</b> 이 단계만 실패할 수 있고(없는/남의 ID → 404),
     *       아래에서 이전 코칭을 취소한 뒤에 터지면 롤백이 되돌려 주더라도 "실패한 요청이
     *       진행 중이던 대화를 지웠다"는 상태를 코드가 허용하게 된다.</li>
     *   <li><b>이전 {@code IN_PROGRESS} 코칭을 {@code CANCELED} 로.</b> 명세 COACH-01 ·
     *       ERD 7장의 규칙이고, 그래서 사용자당 진행 중 세션은 언제나 하나다.</li>
     *   <li>세션과 첫 메시지 저장.</li>
     *   <li>넛지를 {@code COACHING_STARTED} 로. 명세 9장 상태 표가 "서버가 자동 변경 →
     *       보통 NDG-03 호출 불필요"라고 적은 것이 이 줄이다.</li>
     * </ol>
     *
     * <p><b>진행 중 챌린지가 있어도 막지 않는다</b>(명세 COACH-01 · ERD 7장). 막는 것은
     * 확정(COACH-04)뿐이다 — 대화는 해 보고 확정은 못 하는 게 사용자에게 덜 답답하다는
     * 판단이 명세에 들어 있다. 그래서 여기에는 챌린지 조회가 없다. <b>추가하지 말 것</b>:
     * 2026-11-09 에 한 번 막아 봤다가 명세 위반이라 되돌렸다
     * ({@code CoachingStartApiTest.doesNotBlockWhileAChallengeRuns} 가 고정한다).
     *
     * <p>첫 메시지 생성은 {@link CoachingOpeningGenerator} 가 하고 <b>실패하지 않는다</b> —
     * Gemini 가 없거나 죽어도 {@code docs/AI_대체_문구.md} 3장 문구가 들어온다. 그래서
     * {@code try/catch} 가 없고, 명세 COACH-01 에 에러가 정의되지 않은 것과도 맞는다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 {@code nudgeId}
     */
    @Transactional
    public CoachingResponse start(Long userId, StartCoachingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(BusinessException::notFound);
        Nudge nudge = findNudge(userId, request);

        cancelInProgress(userId);

        Coaching coaching = coachingRepository.save(Coaching.start(user));
        CoachingMessage opening = coachingMessageRepository.save(
                CoachingMessage.ai(coaching, coachingOpeningGenerator.generate(openingCommand(user, nudge))));

        if (nudge != null) {
            // 되돌리는 전이가 없는 상태다(NDG-03) — 이미 COACHING_STARTED 인 카드로 다시
            // 시작해도 같은 값을 덮어쓸 뿐이라 따로 비교하지 않는다.
            nudge.changeStatus(NudgeStatus.COACHING_STARTED);
        }

        return CoachingResponse.of(coaching, List.of(opening));
    }

    /**
     * COACH-02 코칭 조회 — <b>재진입 시 대화 복원용</b>이다(명세 COACH-02). 응답 모양은
     * COACH-01 과 같고({@link CoachingResponse}), 담기는 것만 다르다: 지금까지의 전체
     * 메시지와 저장된 최신 제안.
     *
     * <p>지난 대화 <b>목록</b> API 는 없다(명세 COACH-02). 프론트는 ID 를 알고 들어오므로
     * 이 엔드포인트만으로 충분하고, 그래서 사용자별 코칭 목록 쿼리도 만들지 않았다.
     *
     * <p><b>종료된 세션도 열린다.</b> {@code COMPLETED}·{@code CANCELED} 를 거르지 않는 건
     * 명세 COACH-02 에 그런 조건이 없기 때문이고, 그게 맞다 — 확정까지 끝낸 대화를 다시
     * 보는 것은 정상적인 열람이다. 409 {@code COACHING_ALREADY_ENDED} 는 "종료된 코칭에
     * <i>요청</i>"(서버_내부_처리 6장), 즉 COACH-03·04 의 <b>쓰기</b> 규칙이지 읽기 규칙이
     * 아니다. NDG-01 의 노출 조건이 NDG-02 상세에는 걸리지 않는 것과 같은 판단이다.
     *
     * <p>없는 코칭과 남의 코칭은 모두 404 다(명세 0장 5)). 조건이 쿼리에 들어 있어
     * ({@code findByIdAndUserId}) 두 경우의 응답이 구조적으로 같다 — ID 를 순서대로 넣어
     * 보는 것만으로 다른 사용자의 코칭 존재 여부를 알 수 없다.
     *
     * <p>메시지 정렬은 쿼리가 책임진다(명세 COACH-02 "{@code createdAt} 오름차순") —
     * {@link CoachingResponse#of} 는 받은 순서를 그대로 쓴다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 코칭
     */
    @Transactional(readOnly = true)
    public CoachingResponse getCoaching(Long userId, Long id) {
        Coaching coaching = coachingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::notFound);

        return CoachingResponse.of(
                coaching,
                coachingMessageRepository.findByCoachingIdOrderByCreatedAtAscIdAsc(coaching.getId()));
    }

    /**
     * {@code nudgeId} 를 보내지 않았으면 {@code null} 이고, 그건 정상이다(넛지 없이 시작한
     * 코칭 — 명세 COACH-01 의 {@code nudgeId} 는 선택).
     *
     * <p><b>보냈는데 찾을 수 없으면 404 다.</b> 명세 COACH-01 에는 {@code Errors} 줄이
     * 없지만, 조회를 {@code findByIdAndUserId} 로 하는 이상 "없는 넛지"와 "남의 넛지"는
     * 구분되지 않고 404 여야 한다(명세 0장 5)). 조용히 무시하고 일반 인사말로 시작하는
     * 선택도 가능했지만, 그러면 프론트가 잘못된 ID 를 보내고 있다는 사실이 사용자에게
     * "맥락 없는 AI" 로만 보인다.
     */
    private Nudge findNudge(Long userId, StartCoachingRequest request) {
        if (!request.hasNudgeId()) {
            return null;
        }
        return nudgeRepository.findByIdAndUserId(request.nudgeId(), userId)
                .orElseThrow(BusinessException::notFound);
    }

    /**
     * ERD 7장 "새 코칭을 시작하면 같은 사용자의 {@code IN_PROGRESS} 코칭을
     * {@code CANCELED} 로". 보통 0건이거나 1건이다.
     */
    private void cancelInProgress(Long userId) {
        coachingRepository.findAllByUserIdAndStatus(userId, CoachingStatus.IN_PROGRESS)
                .forEach(Coaching::cancel);
    }

    /**
     * 넛지가 있으면 그 카드의 숫자를 첫 메시지 맥락으로 넘긴다
     * ({@code docs/AI_대체_문구.md} 3장의 두 갈래).
     *
     * <p>엔티티가 아니라 값만 넘긴다 — AI 포트는 DB·JPA 를 몰라야 한다
     * ({@code CoachingOpeningCommand} 설명). 지연 로딩된 카테고리는 이 트랜잭션 안에서
     * 읽히므로 여기서 꺼내는 게 안전한 유일한 자리이기도 하다.
     */
    private static CoachingOpeningCommand openingCommand(User user, Nudge nudge) {
        if (nudge == null) {
            return CoachingOpeningCommand.withoutNudge(user.getUsername());
        }
        return CoachingOpeningCommand.fromNudge(
                user.getUsername(),
                nudge.getCategory().getName(),
                nudge.getSpendingAmount(),
                nudge.getAvgAmount());
    }
}

package com.back.was.domain.coaching;

import com.back.was.domain.ai.CoachingOpeningCommand;
import com.back.was.domain.ai.CoachingOpeningGenerator;
import com.back.was.domain.ai.CoachingReply;
import com.back.was.domain.ai.CoachingReplyCommand;
import com.back.was.domain.ai.CoachingReplyGenerator;
import com.back.was.domain.ai.CoachingTurn;
import com.back.was.domain.ai.ProposedGoal;
import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeStatus;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.domain.coaching.dto.CoachingResponse;
import com.back.was.domain.coaching.dto.ConfirmGoalRequest;
import com.back.was.domain.coaching.dto.ConfirmGoalResponse;
import com.back.was.domain.coaching.dto.SendMessageRequest;
import com.back.was.domain.coaching.dto.SendMessageResponse;
import com.back.was.domain.coaching.dto.StartCoachingRequest;
import com.back.was.domain.nudge.Nudge;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.nudge.NudgeStatus;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 명세 10장 AI 코칭(COACHING) — COACH-01~04. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachingService {

    private final CoachingRepository coachingRepository;
    private final CoachingMessageRepository coachingMessageRepository;
    private final NudgeRepository nudgeRepository;
    private final ChallengeRepository challengeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CoachingOpeningGenerator coachingOpeningGenerator;
    private final CoachingReplyGenerator coachingReplyGenerator;
    private final CoachingReplyFallback coachingReplyFallback;

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
     * COACH-03 메시지 전송. 사용자 메시지와 AI 응답을 <b>한 트랜잭션에서</b> 저장하고,
     * AI 가 목표를 제안했으면 {@code PROPOSAL_} 컬럼 6개를 덮어쓴다(서버_내부_처리 6장).
     *
     * <p><b>AI 가 실패해도 200 이다.</b> 명세 0장 4) 각주와 서버_내부_처리 8장이 코칭 대화는
     * 에러 대신 대체 메시지로 응답하라고 정했다 — 그래서 포트 호출만 {@code try} 로 감싸고
     * ({@link CoachingReplyGenerator} 는 일부러 던지는 포트다) 실패하면
     * {@link CoachingReplyFallback} 이 만든 응답에 {@code isFallback: true} 를 붙인다.
     * <b>Gemini 키가 없거나 한도·장애일 때 이 경로가 타진다.</b>
     *
     * <p>{@code RuntimeException} 을 통째로 잡는다. 8장은 어댑터가 실패를
     * {@code AI_UNAVAILABLE} 로 변환하도록 정하지만, 변환을 빠뜨린 예외 하나가 500 으로
     * 새면 "에러를 내지 않는다"는 규칙이 깨진다 — 대화는 대체 문구로라도 이어지는 쪽이
     * 명세에 맞다. 원인은 로그에 남긴다.
     *
     * <p>순서가 규칙이다: <b>사용자 메시지를 먼저 저장</b>해야 AI 에게 넘길 맥락의 마지막
     * 줄이 방금 받은 질문이 된다(ERD 8장). 그 다음 응답 → 저장 → 제안 반영.
     *
     * <p>종료된 세션이면 409 {@code COACHING_ALREADY_ENDED} 다(명세 COACH-03 Errors ·
     * 서버_내부_처리 6장 "종료된 코칭에 요청"). {@code COMPLETED} 와 {@code CANCELED} 를
     * 묶어서 묻는 것은 {@link Coaching#isEnded()} 다 — 읽기(COACH-02)에는 걸리지 않는
     * 쓰기 전용 규칙이다.
     *
     * <p>외부 호출이 트랜잭션 안에 있는 것은 감수한 비용이다. 서버_내부_처리 6장이 두
     * 메시지를 함께 저장하라고 하고 응답에 둘 다 필요하므로 커밋을 미룰 수밖에 없다 —
     * 포트 계약의 10초 타임아웃(8장)이 그 시간의 상한이다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 코칭,
     *                           409 {@code COACHING_ALREADY_ENDED} — 종료된 코칭
     */
    @Transactional
    public SendMessageResponse sendMessage(Long userId, Long id, SendMessageRequest request) {
        Coaching coaching = coachingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::notFound);
        if (coaching.isEnded()) {
            throw new BusinessException(ErrorCode.COACHING_ALREADY_ENDED);
        }

        CoachingMessage userMessage = coachingMessageRepository.save(
                CoachingMessage.user(coaching, request.content()));

        boolean fallback = false;
        CoachingReply reply;
        try {
            reply = coachingReplyGenerator.reply(replyCommand(coaching));
        } catch (RuntimeException e) {
            log.warn("코칭 {} AI 응답 실패 — 대체 문구로 응답한다", coaching.getId(), e);
            reply = coachingReplyFallback.of(coaching.getUser());
            fallback = true;
        }

        CoachingMessage aiMessage = coachingMessageRepository.save(
                CoachingMessage.ai(coaching, reply.message()));
        boolean proposed = applyProposal(coaching, reply.proposal());

        return SendMessageResponse.of(userMessage, aiMessage, fallback, coaching, proposed);
    }

    /**
     * COACH-04 목표 확정 — 요청의 최종 값으로 {@code CHALLENGE} 를 만들고 코칭을
     * {@code COMPLETED} 로 끝낸다 (명세 COACH-04 · 서버_내부_처리 6장).
     *
     * <p><b>저장된 제안과 비교하지 않는다.</b> 사용자가 ± 와 기간 버튼으로 바꾼 값은
     * 저장되지 않고 이 요청에 한 번에 실려 오고(명세 COACH-03), 확정 이후의 실적·판정
     * 기준은 <b>챌린지 값</b>이다(ERD 7장). 제안 컬럼은 "AI 제안을 그대로 확정한 비율" 같은
     * 기록으로 남는다. 반대로 <b>확정하지 않은 제안은 챌린지가 되지 않는다</b>(SRS 7.3-3) —
     * 챌린지를 만드는 경로가 이 메서드뿐이라 그게 저절로 성립한다.
     *
     * <p>두 개의 409 를 <b>이 순서로</b> 본다:
     *
     * <ol>
     *   <li>종료된 코칭이면 {@code COACHING_ALREADY_ENDED}. 경로가 지목한 리소스에 대한
     *       판단이라 먼저 본다 — 같은 코칭을 두 번 확정했을 때(더블 클릭) 사용자에게 더
     *       정확한 설명이기도 하다. 두 조건이 동시에 참인 유일한 경우가 그것이다.</li>
     *   <li>진행 중 챌린지가 있으면 {@code CHALLENGE_ALREADY_IN_PROGRESS} (ERD 9장
     *       "사용자당 진행 중인 챌린지는 1개"). COACH-01 이 막지 않는 그 검사가
     *       <b>여기</b>에 있다.</li>
     * </ol>
     *
     * <p>{@code CHALLENGE.COACHING_ID} 의 UNIQUE 가 마지막 방어선이다 — 위 순서를 통과한
     * 요청이 같은 코칭으로 두 번 들어오는 일은 1번에서 막히지만, 동시 요청은 인덱스에서
     * 부딪힌다.
     *
     * <p>조합 검증(목표 유형과 목표 값의 짝, 기간)은 {@link #requireValidPeriod} 와
     * {@link Challenge#of} 가 맡는다 — ERD 9장의 체크 조건을 DTO 에 한 번 더 적지 않고
     * 엔티티가 던지는 것을 명세의 400 으로 번역한다. 규칙을 두 벌로 두면 어긋난다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 코칭, 없는 카테고리 /
     *                           409 {@code COACHING_ALREADY_ENDED} /
     *                           409 {@code CHALLENGE_ALREADY_IN_PROGRESS} /
     *                           400 {@code VALIDATION_ERROR} — 기간·목표 조합 위반
     */
    @Transactional
    public ConfirmGoalResponse confirmGoal(Long userId, Long id, ConfirmGoalRequest request) {
        Coaching coaching = coachingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(BusinessException::notFound);
        if (coaching.isEnded()) {
            throw new BusinessException(ErrorCode.COACHING_ALREADY_ENDED);
        }
        if (challengeRepository.existsByCoachingUserIdAndStatus(userId, ChallengeStatus.IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.CHALLENGE_ALREADY_IN_PROGRESS);
        }

        requireValidPeriod(request);
        Category category = expenseCategory(request.categoryId());

        Challenge challenge = challengeRepository.save(challengeOf(coaching, category, request));
        coaching.complete();

        return ConfirmGoalResponse.of(challenge, coaching);
    }

    /**
     * 명세 COACH-04: "{@code endDate} 는 {@code startDate} 이후. 기간은 7·14·30일 중 선택".
     *
     * <p>역순 날짜도 여기서 걸린다 — 기간이 0 이하가 되어 허용 목록에 없기 때문이다.
     * {@code details} 없이 메시지만 내려보내는 이유는 {@code ConfirmGoalRequest} 의 설명에
     * 있다(명세가 이 규칙의 {@code reason} 을 정해 두지 않았다).
     */
    private static void requireValidPeriod(ConfirmGoalRequest request) {
        if (!request.hasAllowedPeriod()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "챌린지 기간은 7일·14일·30일 중 하나여야 해요.");
        }
    }

    /**
     * 명세 COACH-04 의 "{@code categoryId}: 지출 카테고리".
     *
     * <p>없는 ID 는 404 다 — TX-01 이 없는 카테고리에 쓰는 코드와 같다(명세 TX-01 Errors).
     * 있지만 수입 카테고리면 400 인데, 그건 "없음"이 아니라 요청 표의 규칙 위반이고
     * 카테고리 목록은 CAT-01 로 누구나 볼 수 있어 숨길 것도 없다.
     */
    private Category expenseCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(BusinessException::notFound);
        if (category.getType() != CategoryType.EXPENSE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "절약 목표는 지출 카테고리에만 걸 수 있어요.");
        }
        return category;
    }

    /**
     * ERD 9장 스냅샷. 엔티티가 체크 조건(유형에 맞는 목표 값, {@code endDate} ≥
     * {@code startDate})을 들고 있으므로, 그 위반을 명세의 400 으로 옮기기만 한다.
     */
    private static Challenge challengeOf(Coaching coaching, Category category,
                                         ConfirmGoalRequest request) {
        try {
            return Challenge.of(coaching, category, request.title(), request.goalType(),
                    request.goalAmount(), request.goalCount(),
                    request.startDate(), request.endDate());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

    /**
     * 대화 전체와 고를 수 있는 지출 카테고리를 모아 포트에 넘긴다.
     *
     * <p>방금 저장한 사용자 메시지까지 포함해야 하므로 저장 <b>후</b>에 다시 조회한다 —
     * 메모리에서 이어 붙이면 정렬 규칙이 쿼리와 두 벌이 된다. 수입 카테고리는 제외한다:
     * 절약 목표를 수입에 걸 수는 없다.
     */
    private CoachingReplyCommand replyCommand(Coaching coaching) {
        List<CoachingTurn> conversation =
                coachingMessageRepository.findByCoachingIdOrderByCreatedAtAscIdAsc(coaching.getId())
                        .stream()
                        .map(message -> new CoachingTurn(message.getRole(), message.getContent()))
                        .toList();
        List<CategoryResponse> categories = categoryRepository.findAllByOrderByIdAsc().stream()
                .filter(category -> category.getType() == CategoryType.EXPENSE)
                .map(CategoryResponse::from)
                .toList();

        return new CoachingReplyCommand(coaching.getUser().getUsername(), conversation, categories);
    }

    /**
     * 제안을 ERD 7장 {@code PROPOSAL_} 컬럼에 덮어쓴다 (최신 1개만 보관).
     *
     * <p><b>저장하기 전에 전부 검증한다.</b> 값의 출처가 Gemini 일 수 있고, 저장된 제안은
     * 사용자가 그대로 확정하면 챌린지가 된다 — 없는 카테고리나 유형에 맞지 않는 목표가
     * 들어가면 COACH-04 에서 터지거나 틀린 챌린지가 만들어진다. 통과하지 못하면 제안만
     * 버리고 <b>메시지는 그대로 내려보낸다</b>: TX-07 이 모르는 카테고리를 "기타"로
     * 추측하지 않고 추출 실패로 처리한 것과 같은 판단이다.
     *
     * <p>제목이 비어 있으면 {@link GoalTitle} 로 채운다 — 명세 COACH-04 "기본값은 AI 제안
     * 제목, 없으면 템플릿".
     *
     * @return 제안이 실제로 저장되면 {@code true}. 응답의 {@code proposal} 키가 이 값을 따른다
     */
    private boolean applyProposal(Coaching coaching, ProposedGoal goal) {
        if (goal == null) {
            return false;
        }
        if (goal.goalType() == null || goal.periodDays() == null || goal.categoryId() == null) {
            log.warn("코칭 {} 제안에 필수 값이 비어 제안을 버린다: {}", coaching.getId(), goal);
            return false;
        }

        Category category = categoryRepository.findById(goal.categoryId()).orElse(null);
        if (category == null || category.getType() != CategoryType.EXPENSE) {
            log.warn("코칭 {} 제안의 카테고리 {} 가 지출 카테고리가 아니어서 제안을 버린다",
                    coaching.getId(), goal.categoryId());
            return false;
        }

        try {
            coaching.propose(category, titleOf(goal, category), goal.goalType(),
                    goal.goalAmount(), goal.goalCount(), goal.periodDays());
            return true;
        } catch (IllegalArgumentException e) {
            // ERD 7장 체크 조건 위반 (유형에 맞는 목표 값 없음, 기간 0 이하)
            log.warn("코칭 {} 제안이 ERD 7장 조건을 어겨 제안을 버린다: {}", coaching.getId(), goal, e);
            return false;
        }
    }

    private static String titleOf(ProposedGoal goal, Category category) {
        if (goal.title() != null && !goal.title().isBlank()) {
            return goal.title();
        }
        Integer target = goal.goalType() == GoalType.AMOUNT ? goal.goalAmount() : goal.goalCount();

        // 목표 값이 비어 있으면 propose 가 거부할 상황이라 제목도 의미가 없다. 그 판단은
        // 한 곳(Coaching.propose)에만 두고, 여기서는 0 으로 템플릿만 채운다.
        return GoalTitle.of(category.getName(), goal.goalType(), target == null ? 0 : target);
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

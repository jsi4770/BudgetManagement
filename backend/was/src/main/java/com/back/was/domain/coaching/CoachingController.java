package com.back.was.domain.coaching;

import com.back.was.domain.coaching.dto.CoachingResponse;
import com.back.was.domain.coaching.dto.ConfirmGoalRequest;
import com.back.was.domain.coaching.dto.ConfirmGoalResponse;
import com.back.was.domain.coaching.dto.SendMessageRequest;
import com.back.was.domain.coaching.dto.SendMessageResponse;
import com.back.was.domain.coaching.dto.StartCoachingRequest;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10. AI 코칭 (COACH)", description = """
        AI 코치와 대화하고 절약 목표를 확정합니다. 세션은 항상 토큰 주인의 것입니다.

        > ⚠️ **아직 구현되지 않았습니다** — 지금은 모든 엔드포인트가 `500 INTERNAL_ERROR` 를 반환합니다.""")
@RestController
@RequestMapping("/coachings")
@RequiredArgsConstructor
public class CoachingController {

    private final CoachingService coachingService;

    /**
     * <b>본문이 없어도 된다</b>({@code required = false}). 유일한 필드인
     * {@code nudgeId} 가 선택이라 "넛지 없이 시작"은 보낼 값이 없는 요청이고, 그때
     * {@code @RequestBody} 기본값({@code required = true})은
     * {@code HttpMessageNotReadableException} → 400 을 만든다. 명세 COACH-01 에 없는
     * 에러이므로 {@link StartCoachingRequest#emptyIfNull} 로 빈 요청과 같게 다룬다.
     *
     * <p>검증 애너테이션이 없으므로 {@code @Valid} 도 없다 — {@code nudgeId} 의 유효성은
     * DB 를 봐야 알 수 있고, 서비스가 404 로 답한다.
     */
    @Operation(summary = "COACH-01 코칭 시작", description = """
            세션과 AI 첫 메시지를 함께 만듭니다. **본문이 없어도 됩니다.**

            `nudgeId` 를 주면 첫 메시지가 그 카드의 숫자를 인용하고, 해당 넛지가 `COACHING_STARTED` 로
            바뀝니다(NDG-03 을 따로 부를 필요 없음). 넛지 자체는 코칭에 저장되지 않습니다.

            같은 사용자의 진행 중 코칭은 `CANCELED` 로 밀려납니다.
            **진행 중 챌린지가 있어도 대화는 시작됩니다** — 막는 것은 확정(COACH-04)뿐입니다.

            `proposal` 은 항상 `null` 로 시작합니다.

            **실패**: `404 NOT_FOUND` — 없는/남의 `nudgeId`""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoachingResponse> start(@AuthenticationPrincipal AuthUser authUser,
                                               @RequestBody(required = false) StartCoachingRequest request) {
        return ApiResponse.ok(
                coachingService.start(authUser.id(), StartCoachingRequest.emptyIfNull(request)));
    }

    @Operation(summary = "COACH-02 코칭 조회 (재진입)", description = """
            COACH-01 과 **같은 형식**입니다(배열이 아닌 단일 객체). 지금까지의 전체 메시지를
            `createdAt` 오름차순으로, 그리고 저장된 최신 `proposal` 을 함께 줍니다 → 제안 카드 복원.

            지난 대화 목록 API 는 없습니다. **종료된 코칭도 열립니다** — 409 는 쓰기(COACH-03·04) 규칙입니다.

            **실패**: `404 NOT_FOUND`""")
    @GetMapping("/{id}")
    public ApiResponse<CoachingResponse> getCoaching(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long id) {
        return ApiResponse.ok(coachingService.getCoaching(authUser.id(), id));
    }

    /** AI 실패에도 200 을 내는 이유는 {@code CoachingService.sendMessage} 에 있다. */
    @Operation(summary = "COACH-03 메시지 전송 → AI 응답", description = """
            사용자 메시지와 AI 응답을 함께 저장하고 둘 다 돌려줍니다. `content` 는 1~500자.

            `aiMessage.isFallback` 이 `true` 면 Gemini 실패로 대체 문구를 쓴 것입니다 —
            프론트는 "AI 연결이 원활하지 않아요"를 안내하세요. 이 값은 저장되지 않아서
            COACH-02 로 다시 읽으면 그 키가 없습니다.

            `proposal` 은 **이번 응답에서 제안한 경우만** 값이 있습니다 — 이전 턴의 제안이 저장돼
            있어도 이번에 제안하지 않았으면 `null` 입니다(저장된 것을 보려면 COACH-02).
            재제안이 필요하면 메시지를 다시 보내세요.

            목표 ±·기간 버튼은 **프론트 상태로만** 바꾸고 COACH-04 에서 한 번에 보냅니다.

            **실패**: `400 VALIDATION_ERROR`, `404 NOT_FOUND`, `409 COACHING_ALREADY_ENDED`""")
    @PostMapping("/{id}/messages")
    public ApiResponse<SendMessageResponse> sendMessage(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id,
                                                        @RequestBody @Valid SendMessageRequest request) {
        return ApiResponse.ok(coachingService.sendMessage(authUser.id(), id, request));
    }

    /**
     * {@code /confirm} 과 {@code /messages} 는 둘 다 리터럴이라 서로, 그리고 COACH-02 의
     * {@code /{id}} 와도 겹치지 않는다 (메서드도 GET/POST 로 다르다).
     */
    @Operation(summary = "COACH-04 목표 확정 → 챌린지 생성", description = """
            요청의 **최종 값**으로 챌린지를 만들고 코칭을 `COMPLETED` 로 끝냅니다.
            응답의 `challengeCreatedAt` 이 곧 코칭 종료 시각입니다.

            값이 저장된 제안과 **달라도 됩니다**(± 조정) — 서버는 비교하지 않고, 확정 이후의
            실적·판정 기준은 챌린지 값입니다. 확정하지 않은 제안은 챌린지가 되지 않습니다.

            `startDate` 는 오늘 이후, 기간은 **7·14·30일** 중 하나여야 합니다
            (`endDate` = `startDate` + 기간 − 1).

            **실패**: `400 VALIDATION_ERROR`(필수값·기간·목표 유형 불일치, `startDate` 가 과거면
            `details[].reason: PAST_DATE`), `404 NOT_FOUND`(없는/남의 코칭, 없는 카테고리),
            `409 COACHING_ALREADY_ENDED`, `409 CHALLENGE_ALREADY_IN_PROGRESS`""")
    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConfirmGoalResponse> confirmGoal(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id,
                                                        @RequestBody @Valid ConfirmGoalRequest request) {
        return ApiResponse.ok(coachingService.confirmGoal(authUser.id(), id, request));
    }
}

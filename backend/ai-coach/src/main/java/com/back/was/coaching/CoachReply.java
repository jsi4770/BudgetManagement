package com.back.was.coaching;

/**
 * 코칭 메시지 1회 응답.
 *
 * @param reply        AI 답변
 * @param proposedGoal 이번 턴에 제안한 목표 (없으면 null). 사용자가 확정(COACH-04)하기 전엔 저장하지 않는다 (SRS 7.3-3)
 * @param fallback     Gemini 실패로 대체 메시지를 보낸 경우 true
 */
public record CoachReply(String reply, GoalProposal proposedGoal, boolean fallback) {}

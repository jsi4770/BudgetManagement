package com.back.was.nudge;

/** 넛지 저장 후 AI 문구 생성을 요청하는 이벤트 (트랜잭션 커밋 후 비동기 처리) */
public record NudgeCopyRequested(Long nudgeId, NudgeFacts facts) {}

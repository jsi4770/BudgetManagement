package com.back.was.nudge;

import java.time.LocalDate;
import java.util.Optional;

/** 넛지 판정이 NUDGE 테이블에 접근하는 창구. NudgeStorePortAdapter 에 구현 */
public interface NudgeStorePort {

    /** 같은 사용자·카테고리·주(analysisStartDate)의 넛지 (상태 무관, DISMISSED 포함) */
    Optional<NudgeRecord> findThisWeek(Long userId, Long categoryId, LocalDate weekStart);

    /** id 가 null 이면 새로 저장, 있으면 값 갱신. 저장된 것(id 포함)을 반환 */
    NudgeRecord save(NudgeRecord nudge);

    void delete(Long nudgeId);

    /** 비동기로 생성된 AI 문구 저장 (NDG-02 aiAnalysis, aiRecommendation). 별도 스레드에서 호출됨 */
    void saveAiCopy(Long nudgeId, String aiAnalysis, String aiRecommendation);
}

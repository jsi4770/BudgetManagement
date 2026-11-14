package com.back.was.domain.challenge;

import com.back.was.global.support.KstTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 일일 챌린지 종료 판정 작업 — 서버_내부_처리 4장 · 11장 ("00:05 KST").
 *
 * <p><b>판정 자체는 {@link ChallengeTermination} 이 하고 여기는 대상을 골라 넘기기만
 * 한다.</b> 두 빈으로 나뉜 이유가 트랜잭션이다: 반복문이 같은 빈의
 * {@code @Transactional} 메서드를 부르면 프록시를 거치지 않아 애너테이션이 조용히 무시되고,
 * 그러면 전체가 한 트랜잭션에 묶여 한 건의 실패가 그날 판정 전부를 되돌린다.
 *
 * <p><b>조건 기반이라 밀린 날짜까지 따라잡는다</b>(4장). "어제 끝난 것"이 아니라 "끝났는데
 * 아직 {@code IN_PROGRESS} 인 전부"를 집으므로 서버가 며칠 꺼져 있었어도 다음 실행 한
 * 번으로 복구된다 — 실패한 날을 기억할 저장소가 필요 없다.
 *
 * <p><b>단일 인스턴스 전제다.</b> 인스턴스를 늘리면 같은 시각에 여러 대가 같은 대상을 집어
 * 중복 실행된다. 리포트의 {@code CHALLENGE_ID} UNIQUE 와
 * {@code Challenge.finish()} 의 가드가 중복 저장은 막지만, 분산 락이나 리더 선출이 정답이다
 * (`docs/남은_작업_계획.md` 3장 "단일 인스턴스 전제").
 *
 * <p>테스트는 이 클래스를 기다리지 않고 {@link #terminateDue()} 를 직접 부른다 — 크론을
 * 흉내내려면 시계를 조작해야 하고, 그래 봐야 검증되는 건 Spring 의 스케줄링이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeTerminationJob {

    private final ChallengeRepository challengeRepository;
    private final ChallengeTermination challengeTermination;

    /**
     * 서버_내부_처리 11장의 "00:05 KST". {@code zone} 을 명시하는 것이 중요하다 — 빼면 JVM
     * 기본 시간대로 돌아서 "오늘"의 정의가 {@link KstTime} 과 어긋난다.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void run() {
        terminateDue();
    }

    /**
     * 끝났는데 아직 판정되지 않은 챌린지를 전부 처리한다.
     *
     * <p>트랜잭션이 없다. 경계는 건마다 {@link ChallengeTermination#terminate} 가 가지고,
     * 한 건이 터져도 나머지는 계속 판정되어야 한다 — 조건 기반 조회라 실패한 건은 다음
     * 실행이 다시 집는다.
     *
     * @return 판정한 챌린지 수
     */
    public int terminateDue() {
        LocalDate today = KstTime.today();
        List<Challenge> due = challengeRepository
                .findAllByStatusAndEndDateBeforeOrderByEndDateAsc(ChallengeStatus.IN_PROGRESS, today);

        int terminated = 0;
        for (Challenge challenge : due) {
            try {
                challengeTermination.terminate(challenge.getId());
                terminated++;
            } catch (RuntimeException e) {
                log.error("챌린지 {} 종료 판정 실패 — 건너뛴다", challenge.getId(), e);
            }
        }

        if (!due.isEmpty()) {
            log.info("챌린지 종료 판정: 대상 {}건 중 {}건 처리", due.size(), terminated);
        }
        return terminated;
    }
}

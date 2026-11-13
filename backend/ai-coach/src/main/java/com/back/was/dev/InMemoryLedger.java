package com.back.was.dev;

import com.back.was.analysis.TxRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 단독 실행용 메모리 저장소 (팀 DB 연결 전). 어댑터 3개가 같이 쓴다.
 * 팀 프로젝트에 붙일 때는 이 클래스 대신 각 어댑터를 JPA 로 바꾸면 된다.
 */
@Component
public class InMemoryLedger {

    public static final long DEMO_USER_ID = 1L;

    private final Map<Long, List<TxRecord>> txs = new ConcurrentHashMap<>();
    private final Map<Long, LocalDate> signup = new ConcurrentHashMap<>();

    public List<TxRecord> all(Long userId) { return txs.getOrDefault(userId, List.of()); }

    public void add(Long userId, TxRecord t) { txs.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(t); }

    public void clear(Long userId) { txs.remove(userId); }

    public LocalDate signupDate(Long userId) { return signup.getOrDefault(userId, LocalDate.now().minusYears(1)); }

    public void setSignupDate(Long userId, LocalDate d) { signup.put(userId, d); }
}

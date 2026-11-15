package com.back.was.domain.ai.gemini;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Gemini 호출 <b>한 번</b>(재시도·예비 모델 전환 포함)의 시간 상한 (서버_내부_처리 8장 "타임아웃 10초").
 *
 * <p>상한이 시도마다가 아니라 호출 전체에 걸려야 하는 이유: 기본·예비 모델 × (재시도 + 1) 번을 각각
 * 연결 3초 + 읽기 10초씩 기다리면 텍스트 호출이 최악 약 55초가 된다(2026-11-15 발견). 그동안 사용자는
 * 503 을 기다리고, 호출자가 트랜잭션 안이면 DB 커넥션까지 묶인다.
 *
 * <p>두 군데서 쓴다:
 * <ul>
 *   <li>{@link HttpGeminiClient} 가 다음 시도·재시도 대기를 시작하기 전에 남은 시간을 본다.</li>
 *   <li>{@link RequestFactory} 가 시도 하나의 연결·읽기 타임아웃을 남은 시간으로 줄인다 — 이게 없으면
 *       상한 1초 전에 시작한 시도가 10초를 더 쓴다.</li>
 * </ul>
 *
 * <p>둘 사이에 마감 시각을 넘기는 통로가 {@code ThreadLocal} 인 것은 팩토리가 요청 속성을 볼 수 없어서다
 * ({@code prepareConnection} 은 연결과 메서드만 받는다). {@link #within} 이 끝나면 반드시 원래 값으로
 * 되돌리므로 풀 스레드에 값이 남지 않는다.
 */
final class GeminiDeadline {

    private static final ThreadLocal<Instant> CURRENT = new ThreadLocal<>();

    private GeminiDeadline() {
    }

    /** {@code deadline} 을 이 스레드의 마감으로 두고 {@code call} 을 실행한다. */
    static <T> T within(Instant deadline, Supplier<T> call) {
        Instant previous = CURRENT.get();
        CURRENT.set(deadline);
        try {
            return call.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** 마감까지 남은 시간. {@link #within} 밖이면 {@code null} — 상한 없음. */
    static Duration remaining() {
        Instant deadline = CURRENT.get();
        return deadline == null ? null : Duration.between(Instant.now(), deadline);
    }

    /**
     * 연결·읽기 타임아웃을 설정값과 남은 시간 중 <b>짧은 쪽</b>으로 잡는 팩토리. 마감이 지났으면 연결을
     * 열지 않고 {@link SocketTimeoutException} 을 던진다 — {@code RestClientException} 으로 올라가
     * 다른 타임아웃과 똑같이 503 {@code AI_UNAVAILABLE} 이 된다.
     */
    static final class RequestFactory extends SimpleClientHttpRequestFactory {

        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);

            Duration remaining = remaining();
            if (remaining == null) {
                return;
            }
            long millis = remaining.toMillis();
            if (millis <= 0) {
                throw new SocketTimeoutException("Gemini 호출 시간 상한 초과");
            }
            // HttpURLConnection 의 0 은 "무한"이라 1ms 이상만 넘어온다 (위에서 0 이하는 던졌다)
            connection.setConnectTimeout(shorter(connection.getConnectTimeout(), millis));
            connection.setReadTimeout(shorter(connection.getReadTimeout(), millis));
        }

        private static int shorter(int configured, long remainingMillis) {
            int remaining = (int) Math.min(remainingMillis, Integer.MAX_VALUE);
            return configured <= 0 ? remaining : Math.min(configured, remaining);
        }
    }
}

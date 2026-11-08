package com.back.was.global.response;

/**
 * {@code data} 가 {@code null} 이어도 <b>키를 남기는</b> 성공 응답.
 *
 * <p>명세 NDG-01 하나만을 위한 타입이다. 그 엔드포인트는 "넛지 1개 반환, 없으면
 * {@code data: null} (카드 숨김)"이 계약인데, {@link ApiResponse} 는
 * {@code @JsonInclude(NON_NULL)} 로 {@code data}/{@code error} 중 하나를 떨어뜨려야 해서
 * ({@code success} + 정확히 한 개가 공통 형식이다) {@code null} 인 {@code data} 를 아예
 * 내보낼 수 없다.
 *
 * <p>그 애노테이션을 {@code data} 에만 풀면 <b>에러 응답마다 {@code data: null} 이 끼어</b>
 * 명세 0장 3)의 실패 형식이 깨진다. 그래서 공통 타입을 건드리지 않고, 키가 남아야 하는
 * 자리만 이 타입으로 받는다 — 명세 전체에서 {@code data: null} 이 적힌 곳은 NDG-01 뿐이다.
 *
 * <p><b>새 엔드포인트에 습관적으로 쓰지 말 것.</b> 기본은 {@link ApiResponse} 이고, 여기에
 * 해당하는 건 "값이 없음"이 정상 응답이면서 명세가 그 키를 요구하는 경우뿐이다. 실패는
 * 이 타입으로 표현할 수 없다({@code error} 필드가 없다).
 */
public record NullableApiResponse<T>(boolean success, T data) {

    public static <T> NullableApiResponse<T> ok(T data) {
        return new NullableApiResponse<>(true, data);
    }
}

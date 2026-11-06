package com.back.was.global.exception.security;

import com.back.was.global.exception.ErrorCode;
import com.back.was.global.exception.ErrorResponse;
import com.back.was.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 필터 체인에서 발생한 에러를 명세 0장 3) 실패 형식으로 직접 쓴다.
 *
 * <p>{@code @RestControllerAdvice}는 필터 단계 예외를 보지 못하므로 메시지 컨버터를
 * 탈 수 없다. 그래서 응답 바디를 손으로 직렬화하되, 본문 타입은 컨트롤러 경로와
 * 똑같은 {@link ApiResponse}를 재사용해 두 경로의 JSON이 어긋나지 않게 한다.
 *
 * <p>{@link ObjectMapper}는 Boot 4가 자동 구성한 Jackson 3 빈을 주입받는다
 * (Boot 4의 주 매퍼는 {@code tools.jackson.databind}이고, {@code com.fasterxml}
 * 2.x도 클래스패스에 함께 있으므로 import를 섞지 않도록 주의한다).
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 메시지가 한국어라 인코딩을 명시하지 않으면 깨진다.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.fail(ErrorResponse.of(errorCode, errorCode.getMessage()));
        objectMapper.writeValue(response.getWriter(), body);
    }
}

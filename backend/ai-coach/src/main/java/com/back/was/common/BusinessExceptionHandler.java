package com.back.was.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * BusinessException → 명세 실패 응답.
 * 팀에 이미 @RestControllerAdvice 가 있으면 아래 두 메서드를 그쪽으로 옮기고 이 파일은 지워도 됩니다.
 * (같은 예외를 두 군데서 잡으면 충돌하니 한 곳에만 두세요)
 */
@RestControllerAdvice
public class BusinessExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponses.fail(e.getCode(), e.getMessage(), e.getDetails()));
    }

    /** 스프링이 업로드 단계에서 용량 초과로 막은 경우 (spring.servlet.multipart.max-file-size) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponses.fail("FILE_TOO_LARGE", "이미지는 10MB 이하만 올릴 수 있어요.", null));
    }
}

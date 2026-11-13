package com.back.was.global.exception;

import com.back.was.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * 모든 예외를 API 명세서 0장 3) 실패 형식으로 변환한다.
 *
 * <p><b>주의</b>: 여기는 인증/인가 실패를 처리하지 <b>못한다</b>.
 * {@code UNAUTHORIZED} · {@code TOKEN_EXPIRED} · {@code FORBIDDEN}은 Spring Security
 * 필터 체인에서 발생해 {@code DispatcherServlet}에 도달하지 않으므로
 * {@code @RestControllerAdvice}를 거치지 않는다. 그 경로는
 * {@link com.back.was.global.exception.security.ApiAuthenticationEntryPoint}와
 * {@link com.back.was.global.exception.security.ApiAccessDeniedHandler}가 담당한다.
 * 여기 있는 {@link AccessDeniedException} 핸들러는 컨트롤러·서비스 <i>안에서</i>
 * 던져진 경우만 잡는다.
 *
 * <p>{@code ResponseEntityExceptionHandler}를 상속하지 않는다. Boot 4는 기본 예외
 * 처리가 {@code ProblemDetail}로 응답하므로, 상속하면 어떤 예외가 명세 형식으로
 * 나가고 어떤 게 ProblemDetail로 새는지 추적이 어려워진다. 평평한 핸들러로 두고
 * 매핑되지 않은 예외는 전부 {@code INTERNAL_ERROR}로 모은다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean Validation 애너테이션 이름 → 명세 {@code details[].reason}.
     * 명세가 확정한 값은 {@code REQUIRED}(0장 3))와 {@code FUTURE_DATE}(TX-01) 둘이고,
     * 표에 없는 애너테이션은 이름을 대문자로 올린 관례값이 된다.
     */
    private static final Map<String, String> REASON_BY_ANNOTATION = Map.of(
            "NotNull", "REQUIRED",
            "NotBlank", "REQUIRED",
            "NotEmpty", "REQUIRED",
            "NotFutureDate", "FUTURE_DATE"
    );

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return build(e.getErrorCode(), e.getMessage(), null);
    }

    /** {@code MethodArgumentNotValidException}은 {@link BindException}의 하위 타입이라 함께 잡힌다. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException e) {
        List<ErrorResponse.Detail> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.Detail(fe.getField(), reasonOf(fe.getCode())))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        List<ErrorResponse.Detail> details = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.Detail(
                        lastNodeOf(v.getPropertyPath().toString()),
                        reasonOf(v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        List<ErrorResponse.Detail> details = List.of(new ErrorResponse.Detail(e.getParameterName(), "REQUIRED"));
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), details);
    }

    /**
     * {@code multipart} 요청에서 파트가 빠진 경우 — RCPT-01 의 {@code image} 가 유일한
     * 사용처다. 위의 쿼리 파라미터 누락과 같은 모양({@code reason: REQUIRED})으로 맞춘다.
     *
     * <p>이 핸들러가 없으면 맨 아래 {@code Exception} 핸들러로 떨어져 <b>500</b> 이 된다 —
     * 파일을 안 붙인 요청은 서버 오류가 아니라 잘못된 요청이다.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        List<ErrorResponse.Detail> details = List.of(new ErrorResponse.Detail(e.getRequestPartName(), "REQUIRED"));
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), details);
    }

    /**
     * 용량 제한은 코드가 아니라 설정이다 — {@code spring.servlet.multipart.max-file-size} 를
     * main·test <b>양쪽</b> properties 에 10MB 로 둔다(Boot 기본값은 1MB). MockMvc 는 컨테이너의
     * multipart 제한을 거치지 않으므로 이 핸들러는 핸들러 단위로만 검증할 수 있다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(MaxUploadSizeExceededException e) {
        return build(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getMessage(), null);
    }

    /** 타입 불일치·깨진 JSON은 어느 필드인지 신뢰할 수 없어 {@code details}를 비운다. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception e) {
        log.debug("malformed request: {}", e.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedType(HttpMediaTypeNotSupportedException e) {
        return build(ErrorCode.UNSUPPORTED_FILE_TYPE, ErrorCode.UNSUPPORTED_FILE_TYPE.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage(), null);
    }

    /** 매핑되지 않은 모든 예외. 원인 메시지는 로그에만 남기고 응답에 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("unhandled exception", e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), null);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message, List<ErrorResponse.Detail> details) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(code, message, details)));
    }

    private static String reasonOf(String annotationName) {
        if (annotationName == null) {
            return "INVALID";
        }
        String mapped = REASON_BY_ANNOTATION.get(annotationName);
        return mapped != null ? mapped : annotationName.toUpperCase();
    }

    private static String lastNodeOf(String propertyPath) {
        int idx = propertyPath.lastIndexOf('.');
        return idx < 0 ? propertyPath : propertyPath.substring(idx + 1);
    }
}

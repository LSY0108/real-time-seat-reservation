package com.demo.seatreservation.global.exception.handler;

import com.demo.seatreservation.common.ErrorResponse;
import com.demo.seatreservation.global.exception.BusinessException;
import com.demo.seatreservation.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외 처리
     *
     * - 서비스 레이어에서 직접 던진 BusinessException 처리
     * - ErrorCode 기준으로 HTTP 상태 코드와 메시지 반환
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.httpStatus())
                .body(ErrorResponse.of(code.name(), e.getMessage(), request.getRequestURI()));
    }

    /**
     * DTO 검증(@Valid) 실패 처리
     *
     * - 필드별 오류 메시지를 Map 형태로 수집
     * - 400 VALIDATION_ERROR 반환
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }

        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity
                .status(code.httpStatus())
                .body(ErrorResponse.of(code.name(), code.message(), request.getRequestURI(), errors));
    }

    /**
     * DB 제약 조건 위반 처리
     *
     * - UNIQUE 제약 충돌 시 발생
     * - (show_id, seat_id) 충돌 → ALREADY_RESERVED 매핑
     * - 기타 제약 위반은 409 CONFLICT로 처리
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e,
            HttpServletRequest request
    ) {
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();

        // Reservation (show_id, seat_id) UNIQUE 충돌 → 이미 예약됨
        if (msg != null && msg.contains("uk_resv_show_seat")) {
            ErrorCode code = ErrorCode.ALREADY_RESERVED;
            return ResponseEntity
                    .status(code.httpStatus())
                    .body(ErrorResponse.of(code.name(), code.message(), request.getRequestURI()));
        }

        return ResponseEntity
                .status(409)
                .body(ErrorResponse.of("CONFLICT", "데이터 제약 조건 위반", request.getRequestURI()));
    }

    /**
     * 최종 예외 처리 (알 수 없는 예외)
     *
     * - 위에서 처리되지 않은 모든 예외를 처리
     * - 운영 환경에서는 반드시 로그 기록 필요
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e, HttpServletRequest request) {
        return ResponseEntity
                .status(500)
                .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류", request.getRequestURI()));
    }
}

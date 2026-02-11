package com.demo.seatreservation.exception.handler;

import com.demo.seatreservation.common.ErrorResponse;
import com.demo.seatreservation.exception.BusinessException;
import com.demo.seatreservation.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.httpStatus())
                .body(ErrorResponse.of(code.name(), e.getMessage(), request.getRequestURI()));
    }

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e, HttpServletRequest request) {
        // 운영에서는 로그 꼭 남기기 (지금은 최소 처리)
        return ResponseEntity
                .status(500)
                .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류", request.getRequestURI()));
    }
}

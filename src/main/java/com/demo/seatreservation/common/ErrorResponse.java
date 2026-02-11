package com.demo.seatreservation.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        String errorCode,
        String message,
        String path,
        LocalDateTime timestamp,
        Object errors
) {
    public static ErrorResponse of(String errorCode, String message, String path) {
        return new ErrorResponse(false, errorCode, message, path, LocalDateTime.now(), null);
    }

    public static ErrorResponse of(String errorCode, String message, String path, Object errors) {
        return new ErrorResponse(false, errorCode, message, path, LocalDateTime.now(), errors);
    }
}

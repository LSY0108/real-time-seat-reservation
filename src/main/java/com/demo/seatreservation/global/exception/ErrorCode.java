package com.demo.seatreservation.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값 검증 실패"),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 실패 (Access Token 없음/만료/유효하지 않음)"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token 위조 또는 불일치"),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token 만료"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일/비밀번호 불일치"),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한 없음"),
    NOT_HOLD_OWNER(HttpStatus.FORBIDDEN, "HOLD 소유자 아님"),
    NOT_RESERVATION_OWNER(HttpStatus.FORBIDDEN, "예약 소유자 아님"),

    // 404
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석 없음"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 없음"),

    // 409
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 다른 사용자가 HOLD 중"),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "HOLD 만료(키 없음)"),
    ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예약 확정됨"),
    ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소됨"),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이메일 중복"),
    SEAT_DUPLICATED(HttpStatus.CONFLICT, "동일 좌석(구역/행/번호) 중복"),
    SEAT_RESERVED_CANNOT_DELETE(HttpStatus.CONFLICT, "예약 확정 좌석 삭제 불가");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}

package com.demo.seatreservation.domain.enums;
/**
 * 사용자 권한을 나타내는 Enum
 *
 * USER  : 일반 사용자 (좌석 조회, HOLD, 예약 등 가능)
 * ADMIN : 관리자 (좌석 생성/수정/삭제, 전체 예약 조회 가능)
 *
 * DB에는 문자열(USER, ADMIN)로 저장됨 (@Enumerated(EnumType.STRING))
 */
public enum Role {
    USER,
    ADMIN
}



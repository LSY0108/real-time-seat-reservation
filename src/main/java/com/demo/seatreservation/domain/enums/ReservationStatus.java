package com.demo.seatreservation.domain.enums;

/**
 * 예약 상태 Enum
 *
 * RESERVED : 예약 확정 상태 (DB에 실제로 남는 상태)
 * CANCELED : 예약 취소 상태
 *
 * ※ HOLD는 DB에 저장하지 않음 (Redis에서 관리)
 */
public enum ReservationStatus {
    RESERVED,
    CANCELED
}

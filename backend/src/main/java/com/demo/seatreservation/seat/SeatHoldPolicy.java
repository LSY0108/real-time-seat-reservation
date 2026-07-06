package com.demo.seatreservation.seat;

/**
 * 좌석 HOLD·예약 관련 정책 상수.
 * SeatHoldService(HOLD 시 잔여 허용량 계산)와 ReservationService(confirm 시 최종 방어)가 함께 참조한다.
 */
public final class SeatHoldPolicy {
    private SeatHoldPolicy() {}

    /** 공연 하나당 유저가 예약(RESERVED)할 수 있는 최대 좌석 수 — 세션 단위가 아닌 평생(전체) 제한 */
    public static final long MAX_SEATS_PER_SHOW = 4L;
}
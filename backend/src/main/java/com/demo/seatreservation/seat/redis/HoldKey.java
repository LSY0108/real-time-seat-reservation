package com.demo.seatreservation.seat.redis;

public final class HoldKey {
    private HoldKey() {}

    public static String of(Long showId, Long seatId) {
        return "hold:" + showId + ":" + seatId;
    }

    // 예매 묶음 키 생성
    public static String bundleOf(Long showId, Long userId) {
        return "hold:bundle:" + showId + ":" + userId;
    }
}
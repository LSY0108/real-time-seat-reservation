package com.demo.seatreservation.seat.redis;

public final class HoldKey {
    private HoldKey() {}

    public static String of(Long showId, Long seatId) {
        return "hold:" + showId + ":" + seatId;
    }
}

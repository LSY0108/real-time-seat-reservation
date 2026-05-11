package com.demo.seatreservation.seat.dto.response;

import com.demo.seatreservation.seat.model.SeatRealTimeStatus;

public class SeatHoldResponse {

    private Long seatId;
    private Long showId;
    private SeatRealTimeStatus status;
    private long expiresInSec;

    public SeatHoldResponse(Long seatId, Long showId, SeatRealTimeStatus status, long expiresInSec) {
        this.seatId = seatId;
        this.showId = showId;
        this.status = status;
        this.expiresInSec = expiresInSec;
    }

    public Long getSeatId() { return seatId; }
    public Long getShowId() { return showId; }
    public SeatRealTimeStatus getStatus() { return status; }
    public long getExpiresInSec() { return expiresInSec; }

    public static SeatHoldResponse held(Long seatId, Long showId, long expiresInSec) {
        return new SeatHoldResponse(seatId, showId, SeatRealTimeStatus.HELD, expiresInSec);
    }
}

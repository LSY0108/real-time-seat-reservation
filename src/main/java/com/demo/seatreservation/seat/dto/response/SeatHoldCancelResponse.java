package com.demo.seatreservation.seat.dto.response;

import com.demo.seatreservation.seat.model.SeatRealTimeStatus;

public class SeatHoldCancelResponse {

    private Long seatId;
    private Long showId;
    private SeatRealTimeStatus status;

    public SeatHoldCancelResponse(Long seatId, Long showId, SeatRealTimeStatus status) {
        this.seatId = seatId;
        this.showId = showId;
        this.status = status;
    }

    public static SeatHoldCancelResponse available(Long seatId, Long showId) {
        return new SeatHoldCancelResponse(seatId, showId, SeatRealTimeStatus.AVAILABLE);
    }

    public Long getSeatId() { return seatId; }
    public Long getShowId() { return showId; }
    public SeatRealTimeStatus getStatus() { return status; }
}
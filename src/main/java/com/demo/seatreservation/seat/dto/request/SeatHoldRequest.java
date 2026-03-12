package com.demo.seatreservation.seat.dto.request;

import jakarta.validation.constraints.NotNull;

public class SeatHoldRequest {

    @NotNull
    private Long showId;

    @NotNull
    private Long userId; // 인증 전 임시

    public SeatHoldRequest() {}

    public Long getShowId() { return showId; }
    public Long getUserId() { return userId; }
}

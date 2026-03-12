package com.demo.seatreservation.seat.dto.request;

import jakarta.validation.constraints.NotNull;

public class SeatHoldCancelRequest {

    @NotNull
    private Long showId;

    @NotNull
    private Long userId;

    public SeatHoldCancelRequest() {}

    public Long getShowId() {
        return showId;
    }

    public Long getUserId() {
        return userId;
    }
}
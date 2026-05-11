package com.demo.seatreservation.seat.dto.request;

import jakarta.validation.constraints.NotNull;

public class SeatHoldCancelRequest {

    @NotNull
    private Long showId;

    public SeatHoldCancelRequest() {}

    public Long getShowId() {
        return showId;
    }
}
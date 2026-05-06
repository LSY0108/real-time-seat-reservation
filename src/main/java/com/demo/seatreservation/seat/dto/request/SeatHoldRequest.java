package com.demo.seatreservation.seat.dto.request;

import jakarta.validation.constraints.NotNull;

public class SeatHoldRequest {

    @NotNull
    private Long showId;

    public SeatHoldRequest() {}

    public Long getShowId() { return showId; }
}

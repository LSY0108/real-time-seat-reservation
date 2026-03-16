package com.demo.seatreservation.seat.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReservationConfirmRequest {

    @NotNull
    private Long seatId;

    @NotNull
    private Long showId;

    @NotNull
    private Long userId;
}
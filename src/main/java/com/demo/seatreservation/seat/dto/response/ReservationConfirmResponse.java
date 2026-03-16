package com.demo.seatreservation.seat.dto.response;

import com.demo.seatreservation.domain.enums.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReservationConfirmResponse {

    private Long seatId;
    private Long showId;
    private ReservationStatus status;
}
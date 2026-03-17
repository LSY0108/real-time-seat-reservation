package com.demo.seatreservation.seat.dto.response;

import com.demo.seatreservation.domain.enums.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MyReservationResponse {

    private Long reservationId;
    private Long seatId;
    private Long showId;
    private ReservationStatus status;
    private LocalDateTime reservedAt;
}

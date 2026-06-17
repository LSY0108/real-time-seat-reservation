package com.demo.seatreservation.seat.dto.response;

import com.demo.seatreservation.domain.enums.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReservationConfirmResponse {

    private Long showId;
    private List<Long> reservedSeatIds;
    private ReservationStatus status;
}
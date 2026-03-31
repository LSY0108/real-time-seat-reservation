package com.demo.seatreservation.seat.controller;

import com.demo.seatreservation.common.ApiResponse;
import com.demo.seatreservation.seat.dto.response.ReservationCancelResponse;
import com.demo.seatreservation.seat.service.ReservationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationCancelController {
    private final ReservationService reservationService;

    public ReservationCancelController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{reservationId}/cancel")
    public ApiResponse<ReservationCancelResponse> cancel(@PathVariable Long reservationId, @RequestParam Long userId) {
        return ApiResponse.ok(reservationService.cancel(reservationId, userId));
    }
}

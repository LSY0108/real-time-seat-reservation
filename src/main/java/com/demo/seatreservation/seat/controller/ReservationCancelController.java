package com.demo.seatreservation.seat.controller;

import com.demo.seatreservation.common.ApiResponse;
import com.demo.seatreservation.seat.dto.response.ReservationCancelResponse;
import com.demo.seatreservation.seat.service.ReservationService;
import com.demo.seatreservation.security.principal.CustomUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationCancelController {
    private final ReservationService reservationService;

    public ReservationCancelController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{reservationId}/cancel")
    public ApiResponse<ReservationCancelResponse> cancel(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.ok(reservationService.cancel(reservationId, principal.getUserId()));
    }
}

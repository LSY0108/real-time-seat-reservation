package com.demo.seatreservation.seat.controller;

import com.demo.seatreservation.common.ApiResponse;
import com.demo.seatreservation.seat.dto.request.ReservationConfirmRequest;
import com.demo.seatreservation.seat.dto.response.ReservationConfirmResponse;
import com.demo.seatreservation.seat.service.ReservationService;
import com.demo.seatreservation.security.principal.CustomUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationConfirmController {
    private final ReservationService reservationService;

    public ReservationConfirmController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/confirm")
    public ApiResponse<ReservationConfirmResponse> confirm(
            @Valid @RequestBody ReservationConfirmRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.ok(reservationService.confirmAll(principal.getUserId(), request));
    }
}

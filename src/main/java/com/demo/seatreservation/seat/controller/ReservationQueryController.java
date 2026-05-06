package com.demo.seatreservation.seat.controller;

import com.demo.seatreservation.common.ApiResponse;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.seat.dto.response.MyReservationResponse;
import com.demo.seatreservation.seat.service.ReservationQueryService;
import com.demo.seatreservation.security.principal.CustomUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class ReservationQueryController {

    private final ReservationQueryService reservationQueryService;

    public ReservationQueryController(ReservationQueryService reservationQueryService) {
        this.reservationQueryService = reservationQueryService;
    }

    @GetMapping("/reservations")
    public ApiResponse<List<MyReservationResponse>> getMyReservations(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) ReservationStatus status
    ) {
        return ApiResponse.ok(
                reservationQueryService.getMyReservations(principal.getUserId(), status)
        );
    }
}
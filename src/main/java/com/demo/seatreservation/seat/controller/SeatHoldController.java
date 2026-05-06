package com.demo.seatreservation.seat.controller;

import com.demo.seatreservation.seat.dto.request.SeatHoldCancelRequest;
import com.demo.seatreservation.seat.dto.response.SeatHoldCancelResponse;
import com.demo.seatreservation.security.principal.CustomUserPrincipal;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.demo.seatreservation.common.ApiResponse;
import com.demo.seatreservation.seat.dto.request.SeatHoldRequest;
import com.demo.seatreservation.seat.dto.response.SeatHoldResponse;
import com.demo.seatreservation.seat.service.SeatHoldService;

@RestController
@RequestMapping("/api/seats")
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    public SeatHoldController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @PostMapping("/{seatId}/hold")
    public ApiResponse<SeatHoldResponse> hold(
            @PathVariable Long seatId,
            @Valid @RequestBody SeatHoldRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.ok(seatHoldService.hold(seatId, principal.getUserId(), request));
    }

    @DeleteMapping("/{seatId}/hold")
    public ApiResponse<SeatHoldCancelResponse> cancelHold(
            @PathVariable Long seatId,
            @Valid @RequestBody SeatHoldCancelRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        return ApiResponse.ok(seatHoldService.cancelHold(seatId, principal.getUserId(), request));
    }

}

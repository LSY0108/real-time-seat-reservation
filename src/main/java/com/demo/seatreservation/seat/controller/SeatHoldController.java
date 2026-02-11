package com.demo.seatreservation.seat.controller;

import jakarta.validation.Valid;

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
            @Valid @RequestBody SeatHoldRequest request
    ) {
        return ApiResponse.ok(seatHoldService.hold(seatId, request));
    }

}

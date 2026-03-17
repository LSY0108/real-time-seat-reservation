package com.demo.seatreservation.seat.controller;

import com.demo.seatreservation.common.ApiResponse;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.seat.dto.response.MyReservationResponse;
import com.demo.seatreservation.seat.service.ReservationQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 내 예약 조회 API
 */
@RestController
@RequestMapping("/api/me")
public class ReservationQueryController {

    private final ReservationQueryService reservationQueryService;

    public ReservationQueryController(ReservationQueryService reservationQueryService) {
        this.reservationQueryService = reservationQueryService;
    }

    // 내 예약 조회 (status 필터 선택 가능)
    @GetMapping("/reservations")
    public ApiResponse<List<MyReservationResponse>> getMyReservations(
            @RequestParam Long userId,
            @RequestParam(required = false) ReservationStatus status
    ) {
        return ApiResponse.ok(
                reservationQueryService.getMyReservations(userId, status)
        );
    }
}
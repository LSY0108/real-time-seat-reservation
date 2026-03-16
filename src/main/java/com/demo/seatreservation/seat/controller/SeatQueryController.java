package com.demo.seatreservation.seat.controller;

import com.demo.seatreservation.common.ApiResponse;
import com.demo.seatreservation.seat.dto.response.SeatQueryResponse;
import com.demo.seatreservation.seat.service.SeatQueryFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
public class SeatQueryController {

    private final SeatQueryFacade seatQueryFacade;

    public SeatQueryController(SeatQueryFacade seatQueryFacade) {
        this.seatQueryFacade = seatQueryFacade;
    }

    @GetMapping
    public ApiResponse<List<SeatQueryResponse>> getSeats(
            @RequestParam Long showId
    ) {
        return ApiResponse.ok(seatQueryFacade.getSeats(showId));
    }
}
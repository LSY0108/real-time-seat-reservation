package com.demo.seatreservation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public String home() {
        return "OK";
    }

    @GetMapping("/health")
    public String health() {
        return "UP";
    }

    @GetMapping("/test-error")
    public void testError() {
        throw new com.demo.seatreservation.exception.BusinessException(
                com.demo.seatreservation.exception.ErrorCode.SEAT_NOT_FOUND
        );
    }
}

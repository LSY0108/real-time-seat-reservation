package com.demo.seatreservation.controller;

import com.demo.seatreservation.global.exception.BusinessException;
import com.demo.seatreservation.global.exception.ErrorCode;
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
        throw new BusinessException(
                ErrorCode.SEAT_NOT_FOUND
        );
    }
}

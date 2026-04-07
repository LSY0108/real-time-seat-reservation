package com.demo.seatreservation.auth.controller;

import com.demo.seatreservation.auth.dto.request.SignupRequest;
import com.demo.seatreservation.auth.dto.response.SignupResponse;
import com.demo.seatreservation.auth.service.AuthService;
import com.demo.seatreservation.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ApiResponse.ok(response);
    }
}

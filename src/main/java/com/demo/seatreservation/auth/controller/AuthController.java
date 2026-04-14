package com.demo.seatreservation.auth.controller;

import com.demo.seatreservation.auth.dto.request.LoginRequest;
import com.demo.seatreservation.auth.dto.request.SignupRequest;
import com.demo.seatreservation.auth.dto.response.*;
import com.demo.seatreservation.auth.service.AuthService;
import com.demo.seatreservation.auth.util.RefreshTokenCookieProvider;
import com.demo.seatreservation.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ApiResponse.ok(response);
    }

    /**
     * 로그인
     *
     * - access token -> body
     * - refresh token -> cookie
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthService.LoginWithRefreshResult result = authService.loginWithRefresh(request);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieProvider.createCookie(result.refreshToken()).toString()
                )
                .body(ApiResponse.ok(result.loginResponse()));

    }
}

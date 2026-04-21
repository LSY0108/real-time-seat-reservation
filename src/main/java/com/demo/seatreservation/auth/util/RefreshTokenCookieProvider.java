package com.demo.seatreservation.auth.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 웹용 Refresh Token 쿠키 생성/삭제 유틸
 */
@Component
public class RefreshTokenCookieProvider {
    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    // Refresh Token 쿠키 생성
    public ResponseCookie createCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth/refresh")
                .maxAge(refreshTokenExpirationMs / 1000)
                .build();
    }

    // Refresh Token 쿠키 삭제
    public ResponseCookie deleteCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth/refresh")
                .maxAge(0)
                .build();
    }
}

package com.demo.seatreservation.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 응답 DTO
 *
 * 웹 기준 응답
 * - access token 은 body 로 반환
 * - refresh token 은 cookie 로 반환
 */
@Getter
@Builder
public class LoginResponse {

    private Long userId;
    private String email;
    private String name;
    private String role;

    private String grantType;
    private String accessToken;
    private Long accessTokenExpiresIn;
    private String sessionId;
}
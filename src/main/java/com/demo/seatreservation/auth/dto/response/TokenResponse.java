package com.demo.seatreservation.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 또는 토큰 재발급 성공 시 클라이언트에 내려주는 토큰 응답 DTO.
 *
 * 포함 정보:
 * - grantType: 토큰 타입 (예: Bearer)
 * - accessToken: API 인증에 사용하는 Access Token
 * - refreshToken: Access Token 재발급에 사용하는 Refresh Token
 * - accessTokenExpiresIn: Access Token 만료 시간(ms)
 * - refreshTokenExpiresIn: Refresh Token 만료 시간(ms)
 * - sessionId: 로그인 세션 식별자
 *
 * 현재 프로젝트는 다중 기기/다중 세션을 고려하므로
 * 각 로그인마다 별도의 sessionId를 발급하고,
 * Refresh Token도 sessionId 단위로 관리한다.
 */

@Getter
@Builder
public class TokenResponse {
    private String grantType;
    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpiresIn;
    private Long refreshTokenExpiresIn;
    private String sessionId;
}

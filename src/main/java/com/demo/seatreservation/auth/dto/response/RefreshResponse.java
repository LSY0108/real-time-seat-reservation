package com.demo.seatreservation.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefreshResponse {
    private String grantType;
    private String accessToken;
    private Long accessTokenExpiresIn;
    private String sessionId;
}
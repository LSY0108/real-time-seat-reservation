package com.demo.seatreservation.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private Long userId;
    private String email;
    private String name;
    private String role;
    private TokenResponse token;
}

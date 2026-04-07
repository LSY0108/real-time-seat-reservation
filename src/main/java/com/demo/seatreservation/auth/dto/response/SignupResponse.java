package com.demo.seatreservation.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupResponse {
    private Long userId;
    private String email;
    private String name;
    private String phone;
}

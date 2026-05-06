package com.demo.seatreservation.seat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.domain.enums.Role;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ReservationCancelControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ReservationRepository reservationRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User saveUser(String email) {
        return userRepository.save(
                User.builder()
                        .email(email)
                        .password(passwordEncoder.encode("password1!"))
                        .name("테스터")
                        .phone("010-0000-0000")
                        .role(Role.USER)
                        .build()
        );
    }

    private String loginAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email": "%s", "password": "password1!"}
                                        """.formatted(email))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("data").get("accessToken").asText();
    }

    @Test
    void cancelReservation_success() throws Exception {
        // 테스트 목적:
        // 예약 취소 요청 시
        // 정상적으로 상태가 CANCELED로 변경되는지 확인
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        Reservation reservation = reservationRepository.save(
                Reservation.builder()
                        .seatId(1L)
                        .showId(1L)
                        .userId(user.getId())
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", reservation.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value(reservation.getId()))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    void cancelReservation_otherUser_returns403() throws Exception {
        // 테스트 목적:
        // 다른 사용자가 예약 취소를 시도하면
        // 403 NOT_RESERVATION_OWNER가 발생해야 한다
        User owner = saveUser("owner@test.com");
        saveUser("other@test.com");
        String otherToken = loginAndGetToken("other@test.com");

        Reservation reservation = reservationRepository.save(
                Reservation.builder()
                        .seatId(1L)
                        .showId(1L)
                        .userId(owner.getId())
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", reservation.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_RESERVATION_OWNER"));
    }

    @Test
    void cancelReservation_alreadyCanceled_returns409() throws Exception {
        // 테스트 목적:
        // 이미 취소된 예약을 다시 취소하면
        // 409 ALREADY_CANCELED가 발생해야 한다
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        Reservation reservation = reservationRepository.save(
                Reservation.builder()
                        .seatId(1L)
                        .showId(1L)
                        .userId(user.getId())
                        .status(ReservationStatus.CANCELED)
                        .build()
        );

        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", reservation.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_CANCELED"));
    }

    @Test
    void cancelReservation_notFound_returns404() throws Exception {
        // 테스트 목적:
        // 존재하지 않는 reservationId로 취소 요청 시
        // 404 RESERVATION_NOT_FOUND가 발생해야 한다
        saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", 9999L)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void cancelReservation_noAuthToken_returns401() throws Exception {
        // 테스트 목적:
        // Authorization 헤더 없이 취소 요청 시 401이 발생해야 한다
        Reservation reservation = reservationRepository.save(
                Reservation.builder()
                        .seatId(1L)
                        .showId(1L)
                        .userId(100L)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", reservation.getId())
                )
                .andExpect(status().isUnauthorized());
    }
}
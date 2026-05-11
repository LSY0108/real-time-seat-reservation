package com.demo.seatreservation.seat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.Seat;
import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.domain.enums.Role;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.repository.SeatRepository;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationQueryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ReservationRepository reservationRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();

        seatRepository.save(
                Seat.builder()
                        .showId(1L)
                        .zone("A")
                        .row(1)
                        .number(1)
                        .build()
        );

        seatRepository.save(
                Seat.builder()
                        .showId(1L)
                        .zone("A")
                        .row(1)
                        .number(2)
                        .build()
        );
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
    void getMyReservations_returnsUserReservations() throws Exception {
        // 테스트 목적:
        // 로그인한 사용자의 예약 목록이 정상적으로 반환되는지 확인
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        Long seatId = seatRepository.findAll().get(0).getId();
        long showId = 1L;

        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId)
                        .showId(showId)
                        .userId(user.getId())
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(
                        get("/api/me/reservations")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].seatId").value(seatId))
                .andExpect(jsonPath("$.data[0].showId").value((int) showId))
                .andExpect(jsonPath("$.data[0].status").value("RESERVED"));
    }

    @Test
    void getMyReservations_withStatusFilter_returnsFilteredReservations() throws Exception {
        // 테스트 목적:
        // status 필터가 있을 경우 해당 상태의 예약만 조회되는지 확인
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        Long seatId1 = seatRepository.findAll().get(0).getId();
        Long seatId2 = seatRepository.findAll().get(1).getId();
        long showId = 1L;

        // RESERVED 예약
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId1)
                        .showId(showId)
                        .userId(user.getId())
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        // CANCELED 예약
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId2)
                        .showId(showId)
                        .userId(user.getId())
                        .status(ReservationStatus.CANCELED)
                        .build()
        );

        mockMvc.perform(
                        get("/api/me/reservations")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .param("status", "RESERVED")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("RESERVED"));
    }

    @Test
    void getMyReservations_emptyResult_returnsEmptyList() throws Exception {
        // 테스트 목적:
        // 해당 사용자의 예약이 없을 경우 빈 리스트가 반환되는지 확인
        saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        mockMvc.perform(
                        get("/api/me/reservations")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getMyReservations_noAuthToken_returns401() throws Exception {
        // 테스트 목적:
        // Authorization 헤더 없이 요청 시 401이 발생해야 한다
        mockMvc.perform(
                        get("/api/me/reservations")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyReservations_excludesOtherUsersReservations() throws Exception {
        // 테스트 목적:
        // 다른 사용자의 예약은 조회되지 않아야 한다
        User user1 = saveUser("user1@test.com");
        User user2 = saveUser("user2@test.com");
        String token1 = loginAndGetToken("user1@test.com");

        Long seatId1 = seatRepository.findAll().get(0).getId();
        Long seatId2 = seatRepository.findAll().get(1).getId();
        long showId = 1L;

        // user1 예약
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId1)
                        .showId(showId)
                        .userId(user1.getId())
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        // user2 예약
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId2)
                        .showId(showId)
                        .userId(user2.getId())
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        // user1 토큰으로 조회 → user1의 예약만 반환
        mockMvc.perform(
                        get("/api/me/reservations")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].seatId").value(seatId1));
    }
}
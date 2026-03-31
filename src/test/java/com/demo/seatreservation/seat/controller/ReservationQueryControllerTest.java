package com.demo.seatreservation.seat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.Seat;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationQueryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ReservationRepository reservationRepository;
    @Autowired SeatRepository seatRepository;

    @BeforeEach
    void setUp() {

        // 테스트 데이터 초기화
        reservationRepository.deleteAll();
        seatRepository.deleteAll();

        // 좌석 2개 생성 (UNIQUE 충돌 방지)
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

    @Test
    void getMyReservations_returnsUserReservations() throws Exception {

        // 테스트 목적:
        // 특정 userId로 예약 조회 시
        // 해당 사용자의 예약 목록이 정상적으로 반환되는지 확인

        Long seatId = seatRepository.findAll().get(0).getId();
        long showId = 1L;
        long userId = 100L;

        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId)
                        .showId(showId)
                        .userId(userId)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(
                        get("/api/me/reservations")
                                .param("userId", String.valueOf(userId))
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
        // status 필터가 있을 경우
        // 해당 상태의 예약만 조회되는지 확인

        Long seatId1 = seatRepository.findAll().get(0).getId();
        Long seatId2 = seatRepository.findAll().get(1).getId();

        long showId = 1L;
        long userId = 100L;

        // RESERVED 예약
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId1)
                        .showId(showId)
                        .userId(userId)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        // CANCELED 예약
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId2)
                        .showId(showId)
                        .userId(userId)
                        .status(ReservationStatus.CANCELED)
                        .build()
        );

        mockMvc.perform(
                        get("/api/me/reservations")
                                .param("userId", String.valueOf(userId))
                                .param("status", "RESERVED")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("RESERVED"));
    }

    @Test
    void getMyReservations_emptyResult_returnsEmptyList() throws Exception {

        // 테스트 목적:
        // 해당 userId의 예약이 없을 경우
        // 빈 리스트가 반환되는지 확인

        long userId = 999L;

        mockMvc.perform(
                        get("/api/me/reservations")
                                .param("userId", String.valueOf(userId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getMyReservations_missingUserId_returns400() throws Exception {

        // 테스트 목적:
        // userId는 필수 파라미터이므로
        // 누락 시 400 Bad Request가 발생해야 한다

        mockMvc.perform(
                        get("/api/me/reservations")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyReservations_excludesOtherUsersReservations() throws Exception {

        // 테스트 목적:
        // 다른 사용자의 예약은 조회되지 않아야 한다

        Long seatId1 = seatRepository.findAll().get(0).getId();
        Long seatId2 = seatRepository.findAll().get(1).getId();

        long showId = 1L;

        long userId1 = 100L;
        long userId2 = 200L;

        // userId=100 예약
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId1)
                        .showId(showId)
                        .userId(userId1)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        // userId=200 예약
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId2)
                        .showId(showId)
                        .userId(userId2)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(
                        get("/api/me/reservations")
                                .param("userId", String.valueOf(userId1))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].seatId").value(seatId1));
    }
}
package com.demo.seatreservation.seat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ReservationCancelControllerTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ReservationRepository reservationRepository;

    @BeforeEach
    void setUp() {

        // 테스트 데이터 초기화
        reservationRepository.deleteAll();
    }

    @Test
    void cancelReservation_success() throws Exception {

        // 테스트 목적:
        // 예약 취소 요청 시
        // 정상적으로 상태가 CANCELED로 변경되는지 확인

        long userId = 100L;

        Reservation reservation = reservationRepository.save(
                Reservation.builder()
                        .seatId(1L)
                        .showId(1L)
                        .userId(userId)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", reservation.getId())
                                .param("userId", String.valueOf(userId))
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

        long ownerId = 100L;
        long otherUserId = 999L;

        Reservation reservation = reservationRepository.save(
                Reservation.builder()
                        .seatId(1L)
                        .showId(1L)
                        .userId(ownerId)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", reservation.getId())
                                .param("userId", String.valueOf(otherUserId))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_RESERVATION_OWNER"));
    }

    @Test
    void cancelReservation_alreadyCanceled_returns409() throws Exception {

        // 테스트 목적:
        // 이미 취소된 예약을 다시 취소하면
        // 409 ALREADY_CANCELED가 발생해야 한다

        long userId = 100L;

        Reservation reservation = reservationRepository.save(
                Reservation.builder()
                        .seatId(1L)
                        .showId(1L)
                        .userId(userId)
                        .status(ReservationStatus.CANCELED)
                        .build()
        );

        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", reservation.getId())
                                .param("userId", String.valueOf(userId))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_CANCELED"));
    }

    @Test
    void cancelReservation_notFound_returns404() throws Exception {

        // 테스트 목적:
        // 존재하지 않는 reservationId로 취소 요청 시
        // 404 RESERVATION_NOT_FOUND가 발생해야 한다

        long userId = 100L;

        mockMvc.perform(
                        post("/api/reservations/{id}/cancel", 9999L)
                                .param("userId", String.valueOf(userId))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void cancelReservation_missingUserId_returns400() throws Exception {

        // 테스트 목적:
        // userId는 필수 파라미터이므로
        // 누락 시 400 Bad Request가 발생해야 한다

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
                .andExpect(status().isBadRequest());
    }
}

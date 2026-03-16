package com.demo.seatreservation.seat.controller;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.Seat;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.repository.SeatRepository;
import com.demo.seatreservation.seat.redis.HoldKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ReservationConfirmControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SeatRepository seatRepository;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {

        // 테스트 시작 전 DB 예약 데이터 초기화
        reservationRepository.deleteAll();

        // Redis 전체 초기화 (HOLD 데이터 제거)
        redisTemplate.getConnectionFactory()
                .getConnection()
                .flushAll();

        // Seat 데이터 초기화
        seatRepository.deleteAll();

        // 테스트용 좌석 1개 생성
        seatRepository.save(
                Seat.builder()
                        .zone("A")
                        .row(1)
                        .number(1)
                        .build()
        );
    }

    @Test
    void confirm_success_shouldReserveSeat() throws Exception {

        // 테스트 목적:
        // 1) 정상적인 HOLD 상태에서 예약 확정 요청 시 200 OK 반환
        // 2) DB에 RESERVED 상태의 예약이 생성되는지 확인
        // 3) Redis HOLD 키가 삭제되는지 확인

        Long seatId = seatRepository.findAll().get(0).getId();
        Long showId = 1L;

        // Redis HOLD 생성 (사용자 100이 좌석 선점)
        String key = HoldKey.of(showId, seatId);
        redisTemplate.opsForValue().set(key, "100");

        // 예약 확정 요청
        mockMvc.perform(post("/api/reservations/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "seatId": %d,
                          "showId": %d,
                          "userId": 100
                        }
                        """.formatted(seatId, showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RESERVED"));

        // DB에 예약이 생성되었는지 확인
        Reservation reservation = reservationRepository.findAll().get(0);

        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RESERVED, reservation.getStatus());

        // Redis HOLD 삭제 확인
        String owner = redisTemplate.opsForValue().get(key);
        org.junit.jupiter.api.Assertions.assertNull(owner);
    }


    @Test
    void confirm_withoutHold_shouldReturn409() throws Exception {

        // 테스트 목적:
        // Redis에 HOLD 키가 존재하지 않는 상태에서
        // 예약 확정 요청 시 409 HOLD_EXPIRED 에러가 발생해야 한다

        Long seatId = seatRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/reservations/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "seatId": %d,
                          "showId": 1,
                          "userId": 100
                        }
                        """.formatted(seatId)))
                .andExpect(status().isConflict());
    }


    @Test
    void confirm_notOwner_shouldReturn403() throws Exception {

        // 테스트 목적:
        // HOLD를 건 사용자와 다른 userId가 예약 확정을 시도할 경우
        // 403 NOT_HOLD_OWNER 에러가 발생해야 한다

        Long seatId = seatRepository.findAll().get(0).getId();
        Long showId = 1L;

        // Redis HOLD 생성 (user 200이 선점)
        String key = HoldKey.of(showId, seatId);
        redisTemplate.opsForValue().set(key, "200");

        // user 100이 예약 확정 시도
        mockMvc.perform(post("/api/reservations/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "seatId": %d,
                          "showId": %d,
                          "userId": 100
                        }
                        """.formatted(seatId, showId)))
                .andExpect(status().isForbidden());
    }


    @Test
    void confirm_twice_shouldReturnAlreadyReserved() throws Exception {

        // 테스트 목적:
        // 동일 좌석에 대해 예약 확정을 두 번 시도할 경우
        // 두 번째 요청은 409 ALREADY_RESERVED 에러가 발생해야 한다

        Long seatId = seatRepository.findAll().get(0).getId();
        Long showId = 1L;

        String key = HoldKey.of(showId, seatId);

        // 첫 번째 HOLD 생성
        redisTemplate.opsForValue().set(key, "100");

        // 첫 번째 confirm (성공)
        mockMvc.perform(post("/api/reservations/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "seatId": %d,
                          "showId": %d,
                          "userId": 100
                        }
                        """.formatted(seatId, showId)))
                .andExpect(status().isOk());

        // 두 번째 confirm을 위해 다시 HOLD 생성
        redisTemplate.opsForValue().set(key, "100");

        // 두 번째 confirm (이미 예약됨 → 실패)
        mockMvc.perform(post("/api/reservations/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "seatId": %d,
                          "showId": %d,
                          "userId": 100
                        }
                        """.formatted(seatId, showId)))
                .andExpect(status().isConflict());
    }
}

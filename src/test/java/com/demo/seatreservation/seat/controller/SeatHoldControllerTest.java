package com.demo.seatreservation.seat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.concurrent.TimeUnit;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.demo.seatreservation.domain.Seat;
import com.demo.seatreservation.repository.SeatRepository;

@SpringBootTest
@AutoConfigureMockMvc
class SeatHoldControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate stringRedisTemplate;
    @Autowired SeatRepository seatRepository;
    @Autowired ReservationRepository reservationRepository;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();

        // 테스트용 Seat 데이터가 없다면 1개 생성해서 테스트가 항상 돌아가게 준비
        if (seatRepository.count() == 0) {
            Seat seat = Seat.builder()
                    .zone("A")
                    .row(1)
                    .number(1)
                    .build();
            seatRepository.save(seat);
        }
    }

    @Test
    void hold_success_createsRedisKeyWithTtl() throws Exception {
        // 테스트 목적:
        // 1) hold 요청이 성공(200)하는지
        // 2) 응답 JSON이 성공 형태로 내려오는지
        // 3) Redis에 hold 키가 생성되고 TTL이 설정되는지(선점이 실제로 걸렸는지)
        Long seatId = seatRepository.findAll().get(0).getId();
        long showId = 1L;
        long userId = 100L;

        String key = "hold:" + showId + ":" + seatId;
        stringRedisTemplate.delete(key);

        mockMvc.perform(
                        post("/api/seats/{seatId}/hold", seatId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "showId": %d,
                                  "userId": %d
                                }
                                """.formatted(showId, userId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.seatId").value(seatId))
                .andExpect(jsonPath("$.data.showId").value((int) showId))
                .andExpect(jsonPath("$.data.status").value("HELD"))
                .andExpect(jsonPath("$.data.expiresInSec").isNumber());

        // Redis 키 생성 확인
        String owner = stringRedisTemplate.opsForValue().get(key);
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);

        org.junit.jupiter.api.Assertions.assertEquals(String.valueOf(userId), owner);
        org.junit.jupiter.api.Assertions.assertNotNull(ttl);
        org.junit.jupiter.api.Assertions.assertTrue(ttl > 0 && ttl <= 300);
    }

    @Test
    void hold_twice_returns409_seatAlreadyHeld() throws Exception {
        // 테스트 목적:
        // 이미 선점된 좌석을 다른 사용자가 다시 hold하려고 하면
        // 409 Conflict로 막히는지(= SEAT_ALREADY_HELD 케이스)
        Long seatId = seatRepository.findAll().get(0).getId();
        long showId = 1L;

        String key = "hold:" + showId + ":" + seatId;
        stringRedisTemplate.delete(key);

        // 1차 hold (성공)
        mockMvc.perform(
                post("/api/seats/{seatId}/hold", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d, "userId": 100}
                                """.formatted(showId))
        ).andExpect(status().isOk());

        // 2차 hold (다른 사용자) -> 충돌(409)
        mockMvc.perform(
                        post("/api/seats/{seatId}/hold", seatId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {"showId": %d, "userId": 200}
                                """.formatted(showId))
                )
                .andExpect(status().isConflict());
    }

    @Test
    void hold_afterTtlExpired_canHoldAgain() throws Exception {
        // 테스트 목적:
        // TTL이 만료되면 선점 키가 사라지고
        // 같은 좌석을 다시 hold 할 수 있어야 한다(재선점 가능)
        Long seatId = seatRepository.findAll().get(0).getId();
        long showId = 1L;

        String key = "hold:" + showId + ":" + seatId;
        stringRedisTemplate.delete(key);

        // 1차 hold 성공
        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"showId": %d, "userId": 100}
            """.formatted(showId)))
                .andExpect(status().isOk());

        // TTL을 테스트용으로 1초로 줄여서 만료시키기
        stringRedisTemplate.expire(key, 1, TimeUnit.SECONDS);
        Thread.sleep(1200);

        // 만료 후 다시 hold -> 성공해야 정상
        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"showId": %d, "userId": 200}
            """.formatted(showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void hold_onReservedSeat_returns409_alreadyReserved() throws Exception {
        // 테스트 목적:
        // DB에 이미 RESERVED 상태의 예약이 있으면
        // hold 요청을 거절해야 한다(= ALREADY_RESERVED 케이스)
        Long seatId = seatRepository.findAll().get(0).getId();
        long showId = 1L;

        // 1. DB에 RESERVED 예약 데이터 미리 넣기
        reservationRepository.save(
                Reservation.builder()
                        .showId(showId)
                        .seatId(seatId)
                        .userId(999L)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        // 2. hold 요청 보내기
        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"showId": %d, "userId": 100}
                    """.formatted(showId)))
                .andExpect(status().isConflict());
    }

    @Test
    void hold_missingShowId_returns400() throws Exception {
        // 테스트 목적:
        // showId는 필수(@NotNull)라서 누락되면 400 Bad Request가 나와야 정상
        Long seatId = seatRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hold_missingUserId_returns400() throws Exception {
        // 테스트 목적:
        // userId는 필수(@NotNull)라서 누락되면 400 Bad Request가 나와야 정상
        Long seatId = seatRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\": 1}"))
                .andExpect(status().isBadRequest());
    }
}

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
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SeatQueryControllerTest {

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

        // DB 예약 데이터 초기화
        reservationRepository.deleteAll();

        // Redis 전체 초기화
        redisTemplate.getConnectionFactory()
                .getConnection()
                .flushAll();

        // Seat 테이블 초기화
        seatRepository.deleteAll();

        // 테스트용 좌석 3개 생성
        seatRepository.save(Seat.builder()
                .zone("A")
                .row(1)
                .number(1)
                .build());

        seatRepository.save(Seat.builder()
                .zone("A")
                .row(1)
                .number(2)
                .build());

        seatRepository.save(Seat.builder()
                .zone("A")
                .row(1)
                .number(3)
                .build());
    }


    /**
     * 테스트 목적:
     * 좌석에 HOLD도 없고 RESERVED도 없는 기본 상황에서
     * 모든 좌석 상태가 AVAILABLE로 조회되는지 확인한다.
     *
     * 기대 결과:
     * - HTTP 200 OK
     * - 모든 좌석 status = AVAILABLE
     */
    @Test
    void getSeats_allAvailable() throws Exception {

        mockMvc.perform(get("/api/seats")
                        .param("showId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[2].status").value("AVAILABLE"));
    }


    /**
     * 테스트 목적:
     * Redis에 HOLD 키가 존재할 경우
     * 좌석 상태가 HELD로 조회되는지 확인한다.
     *
     * 상황:
     * - Redis에 hold:{showId}:{seatId} 키 생성
     *
     * 기대 결과:
     * - 해당 좌석 상태 = HELD
     */
    @Test
    void getSeats_holdSeat_shouldReturnHeld() throws Exception {

        Long seatId = seatRepository.findAll().get(0).getId();
        Long showId = 1L;

        String key = HoldKey.of(showId, seatId);

        redisTemplate.opsForValue()
                .set(key, "100", 300, TimeUnit.SECONDS);

        mockMvc.perform(get("/api/seats")
                        .param("showId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("HELD"));
    }


    /**
     * 테스트 목적:
     * DB에 RESERVED 상태의 예약이 존재할 경우
     * 좌석 상태가 RESERVED로 조회되는지 확인한다.
     *
     * 상황:
     * - Reservation(status = RESERVED) 데이터 존재
     *
     * 기대 결과:
     * - 해당 좌석 상태 = RESERVED
     */
    @Test
    void getSeats_reservedSeat_shouldReturnReserved() throws Exception {

        Long seatId = seatRepository.findAll().get(0).getId();

        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId)
                        .showId(1L)
                        .userId(999L)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(get("/api/seats")
                        .param("showId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("RESERVED"));
    }

    /**
     * 테스트 목적:
     * Redis HOLD와 DB RESERVED가 동시에 존재하는 상황에서
     * 좌석 상태 우선순위 규칙이 올바르게 적용되는지 확인한다.
     *
     * 상황:
     * - DB에 RESERVED 예약 존재
     * - Redis HOLD 키 존재
     *
     * 설계 규칙:
     * 좌석 상태 우선순위
     * RESERVED > HELD > AVAILABLE
     *
     * 기대 결과:
     * - RESERVED 상태가 반환되어야 한다.
     *
     * 이유:
     * confirm 과정에서
     * 1) DB Reservation 저장
     * 2) Redis HOLD 삭제
     * 순서로 처리되기 때문에
     * 짧은 순간 RESERVED + HOLD가 동시에 존재할 수 있다.
     */
    @Test
    void reservedAndHoldTogether_shouldReturnReserved() throws Exception {

        Long seatId = seatRepository.findAll().get(0).getId();
        Long showId = 1L;

        // DB RESERVED
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId)
                        .showId(showId)
                        .userId(999L)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        // Redis HOLD
        String key = HoldKey.of(showId, seatId);
        redisTemplate.opsForValue().set(key, "100");

        mockMvc.perform(get("/api/seats")
                        .param("showId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("RESERVED"));
    }
}
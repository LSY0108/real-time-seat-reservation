package com.demo.seatreservation.seat.controller;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.Seat;
import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.domain.enums.Role;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.repository.SeatRepository;
import com.demo.seatreservation.repository.UserRepository;
import com.demo.seatreservation.seat.redis.HoldKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ReservationConfirmControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired SeatRepository seatRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();

        redisTemplate.getConnectionFactory()
                .getConnection()
                .flushAll();
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

    private Seat createSeat(Long showId, int number) {
        return seatRepository.save(
                Seat.builder()
                        .showId(showId)
                        .zone("A")
                        .row(1)
                        .number(number)
                        .build()
        );
    }

    /** 테스트용 번들 상태 직접 Redis에 세팅 */
    private void setupBundle(Long showId, Long userId, List<Long> seatIds) {
        String bundleKey = HoldKey.bundleOf(showId, userId);
        for (Long seatId : seatIds) {
            redisTemplate.opsForSet().add(bundleKey, String.valueOf(seatId));
            String seatKey = HoldKey.of(showId, seatId);
            redisTemplate.opsForValue().set(seatKey, String.valueOf(userId));
            redisTemplate.expire(seatKey, 300, TimeUnit.SECONDS);
        }
        redisTemplate.expire(bundleKey, 300, TimeUnit.SECONDS);
    }

    @Test
    void confirmAll_success_shouldReserveSeats() throws Exception {
        // 정상 번들 상태에서 confirmAll 요청 시 200 OK 및 DB 예약 생성 확인
        User user = saveUser("confirm@test.com");
        String token = loginAndGetToken("confirm@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        Long showId = 1L;

        setupBundle(showId, user.getId(), List.of(seatId));

        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.reservedSeatIds").isArray())
                .andExpect(jsonPath("$.data.reservedSeatIds[0]").value(seatId));

        // DB에 예약이 생성되었는지 확인
        List<Reservation> reservations = reservationRepository.findAll();
        Assertions.assertEquals(1, reservations.size());
        Assertions.assertEquals(ReservationStatus.RESERVED, reservations.get(0).getStatus());
        Assertions.assertEquals(seatId, reservations.get(0).getSeatId());
    }

    @Test
    void confirmAll_multiSeat_shouldReserveAll() throws Exception {
        // 여러 좌석 번들 → 전부 예약 확정
        User user = saveUser("confirm@test.com");
        String token = loginAndGetToken("confirm@test.com");

        Seat seat1 = createSeat(1L, 1);
        Seat seat2 = createSeat(1L, 2);
        Seat seat3 = createSeat(1L, 3);
        Long showId = 1L;
        List<Long> seatIds = List.of(seat1.getId(), seat2.getId(), seat3.getId());

        setupBundle(showId, user.getId(), seatIds);

        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.reservedSeatIds").isArray());

        List<Reservation> reservations = reservationRepository.findAll();
        Assertions.assertEquals(3, reservations.size());
        reservations.forEach(r -> Assertions.assertEquals(ReservationStatus.RESERVED, r.getStatus()));
    }

    @Test
    void confirmAll_redisCleanedUpAfterCommit() throws Exception {
        // DB 커밋 후 seat 키와 bundle 키가 모두 삭제되는지 확인
        User user = saveUser("confirm@test.com");
        String token = loginAndGetToken("confirm@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        Long showId = 1L;
        String seatKey   = HoldKey.of(showId, seatId);
        String bundleKey = HoldKey.bundleOf(showId, user.getId());

        setupBundle(showId, user.getId(), List.of(seatId));

        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        // seat 키 삭제 확인
        Assertions.assertNull(redisTemplate.opsForValue().get(seatKey));
        // bundle 키 삭제 확인
        Assertions.assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(bundleKey)));
    }

    @Test
    void confirmAll_noBundle_returns409_sessionExpired() throws Exception {
        // 번들 없이 confirmAll 요청 시 SESSION_EXPIRED 409
        saveUser("confirm@test.com");
        String token = loginAndGetToken("confirm@test.com");

        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": 1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"));
    }

    @Test
    void confirmAll_twice_secondReturns409_sessionExpired() throws Exception {
        // 두 번째 confirmAll 요청 시 bundle이 삭제된 상태 → SESSION_EXPIRED 409
        User user = saveUser("confirm@test.com");
        String token = loginAndGetToken("confirm@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        Long showId = 1L;

        setupBundle(showId, user.getId(), List.of(seatId));

        // 첫 번째 confirm 성공
        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        // 두 번째 confirm → bundle 없음 → SESSION_EXPIRED
        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"));
    }

    @Test
    void confirmAll_whenSeatAlreadyReservedInDb_returns409() throws Exception {
        // DB에 이미 RESERVED 예약 존재 → saveAll 시 UNIQUE 위반 → ALREADY_RESERVED 409
        User user = saveUser("confirm@test.com");
        String token = loginAndGetToken("confirm@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        Long showId = 1L;

        // DB에 이미 예약 존재
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatId)
                        .showId(showId)
                        .userId(999L)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        setupBundle(showId, user.getId(), List.of(seatId));

        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_RESERVED"));
    }

    @Test
    void confirmAll_noAuthToken_returns401() throws Exception {
        mockMvc.perform(post("/api/reservations/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": 1}
                                """))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 테스트 목적:
     * bundle에 seatA·seatB 2석이 있을 때, seatA가 이미 DB에 RESERVED 상태이면
     * saveAll + flush 시 UNIQUE 위반으로 트랜잭션 전체가 롤백되어
     * seatB도 저장되지 않아야 한다. (all-or-nothing 보장)
     *
     * 기대 결과:
     * - HTTP 409 ALREADY_RESERVED
     * - DB 예약 건수 = 1 (기존 seatA 예약만 남아 있음)
     * - seatB에 대한 예약이 생성되지 않음
     */
    @Test
    void confirmAll_partialDuplicate_rollbacksAll() throws Exception {
        User user = saveUser("confirm@test.com");
        String token = loginAndGetToken("confirm@test.com");

        Seat seatA = createSeat(1L, 1);
        Seat seatB = createSeat(1L, 2);
        Long showId = 1L;

        // seatA는 다른 사용자가 이미 예약 완료
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seatA.getId())
                        .showId(showId)
                        .userId(999L)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        // bundle에 seatA·seatB 모두 포함
        setupBundle(showId, user.getId(), List.of(seatA.getId(), seatB.getId()));

        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_RESERVED"));

        // seatA의 기존 예약 1건만 남고, seatB는 저장되지 않았어야 한다
        Assertions.assertEquals(1L, reservationRepository.count(),
                "UNIQUE 위반 시 트랜잭션 전체가 롤백되어야 하며 seatB도 저장되면 안 된다");

        // seatB에 대한 현재 사용자의 예약이 없는지 명시적 확인
        boolean seatBReserved = reservationRepository.existsByShowIdAndSeatIdAndStatus(
                showId, seatB.getId(), ReservationStatus.RESERVED);
        Assertions.assertFalse(seatBReserved, "seatB는 예약되지 않아야 한다");
    }

    /**
     * 테스트 목적:
     * HOLD 시점에는 이미 잔여 허용량만큼만 bundle에 담기도록 막지만, confirm 시점에도
     * 공연당 유저 평생 예약 상한(4석)을 한 번 더 방어적으로 검사해야 한다.
     * 이 테스트는 그 방어 로직을 직접 검증하기 위해, HOLD API를 거치지 않고
     * setupBundle로 상한을 초과하는 bundle 상태를 강제로 만든 뒤 confirm을 호출한다.
     *
     * 기대 결과:
     * - 이미 2석을 확정 예약한 유저의 bundle에 3석이 더 있으면(합계 5석) confirm은
     *   HOLD_LIMIT_EXCEEDED(409)로 실패해야 한다.
     * - all-or-nothing: 새로 저장되는 예약이 하나도 없어야 한다 (기존 2건만 유지).
     */
    @Test
    void confirmAll_exceedsLifetimeLimitPerShow_returns409_andSavesNothing() throws Exception {
        User user = saveUser("confirm@test.com");
        String token = loginAndGetToken("confirm@test.com");

        Long showId = 1L;
        List<Seat> seats = List.of(
                createSeat(showId, 1),
                createSeat(showId, 2),
                createSeat(showId, 3),
                createSeat(showId, 4),
                createSeat(showId, 5)
        );

        // 이미 2석(seat 1, 2) 확정 예약된 상태
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seats.get(0).getId())
                        .showId(showId)
                        .userId(user.getId())
                        .status(ReservationStatus.RESERVED)
                        .build()
        );
        reservationRepository.save(
                Reservation.builder()
                        .seatId(seats.get(1).getId())
                        .showId(showId)
                        .userId(user.getId())
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        // HOLD API를 거치지 않고 bundle에 3석(seat 3, 4, 5)을 강제로 채움 → 합계 5석
        List<Long> bundleSeatIds = List.of(seats.get(2).getId(), seats.get(3).getId(), seats.get(4).getId());
        setupBundle(showId, user.getId(), bundleSeatIds);

        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HOLD_LIMIT_EXCEEDED"));

        // 기존 2건만 남아있고, bundle의 3석은 하나도 저장되지 않아야 한다
        Assertions.assertEquals(2L, reservationRepository.count(),
                "평생 상한 초과 시 confirm은 아무 것도 저장하지 않아야 한다");
    }
}
package com.demo.seatreservation.seat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.domain.enums.Role;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.repository.UserRepository;
import com.demo.seatreservation.seat.redis.HoldKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.demo.seatreservation.domain.Seat;
import com.demo.seatreservation.repository.SeatRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SeatHoldControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate stringRedisTemplate;
    @Autowired SeatRepository seatRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();

        stringRedisTemplate.getConnectionFactory()
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

    private List<Seat> createSeats(Long showId, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> createSeat(showId, i))
                .toList();
    }

    @Test
    void hold_success_createsRedisKeyWithTtl() throws Exception {
        // seat hold 키 생성, bundle 키 생성, TTL 설정 모두 확인
        User user = saveUser("hold@test.com");
        String token = loginAndGetToken("hold@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        long showId = 1L;

        String seatKey   = HoldKey.of(showId, seatId);
        String bundleKey = HoldKey.bundleOf(showId, user.getId());

        mockMvc.perform(
                        post("/api/seats/{seatId}/hold", seatId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"showId": %d}
                                        """.formatted(showId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.seatId").value(seatId))
                .andExpect(jsonPath("$.data.showId").value((int) showId))
                .andExpect(jsonPath("$.data.status").value("HELD"))
                .andExpect(jsonPath("$.data.expiresInSec").isNumber());

        // seat hold 키 검증
        String owner = stringRedisTemplate.opsForValue().get(seatKey);
        Long seatTtl = stringRedisTemplate.getExpire(seatKey, TimeUnit.SECONDS);
        Assertions.assertEquals(String.valueOf(user.getId()), owner);
        Assertions.assertNotNull(seatTtl);
        Assertions.assertTrue(seatTtl > 0 && seatTtl <= 300);

        // bundle 키 검증
        Set<String> bundleMembers = stringRedisTemplate.opsForSet().members(bundleKey);
        Long bundleTtl = stringRedisTemplate.getExpire(bundleKey, TimeUnit.SECONDS);
        Assertions.assertNotNull(bundleMembers);
        Assertions.assertTrue(bundleMembers.contains(String.valueOf(seatId)));
        Assertions.assertNotNull(bundleTtl);
        Assertions.assertTrue(bundleTtl > 0 && bundleTtl <= 300);
    }

    @Test
    void hold_noAuthToken_returns401() throws Exception {
        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();

        mockMvc.perform(
                        post("/api/seats/{seatId}/hold", seatId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"showId": 1}
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hold_twice_returns409_seatAlreadyHeld() throws Exception {
        // 이미 선점된 좌석을 다른 사용자가 hold → SEAT_ALREADY_HELD 409
        saveUser("user1@test.com");
        saveUser("user2@test.com");
        String token1 = loginAndGetToken("user1@test.com");
        String token2 = loginAndGetToken("user2@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        long showId = 1L;

        mockMvc.perform(
                post("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId))
        ).andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/seats/{seatId}/hold", seatId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"showId": %d}
                                        """.formatted(showId))
                )
                .andExpect(status().isConflict());
    }

    @Test
    void hold_afterTtlExpired_canHoldAgain() throws Exception {
        // seat hold 키와 bundle 키가 모두 만료된 후 다른 사용자가 같은 좌석을 hold 가능
        User user1 = saveUser("user1@test.com");
        saveUser("user2@test.com");
        String token1 = loginAndGetToken("user1@test.com");
        String token2 = loginAndGetToken("user2@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        long showId = 1L;

        String seatKey    = HoldKey.of(showId, seatId);
        String bundleKey1 = HoldKey.bundleOf(showId, user1.getId());

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        // user1의 seat 키와 bundle 키 모두 만료
        stringRedisTemplate.expire(seatKey, 1, TimeUnit.SECONDS);
        stringRedisTemplate.expire(bundleKey1, 1, TimeUnit.SECONDS);
        Thread.sleep(1500);

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void hold_onReservedSeat_returns409_alreadyReserved() throws Exception {
        saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        long showId = 1L;

        reservationRepository.save(
                Reservation.builder()
                        .showId(showId)
                        .seatId(seatId)
                        .userId(999L)
                        .status(ReservationStatus.RESERVED)
                        .build()
        );

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict());
    }

    @Test
    void hold_missingShowId_returns400() throws Exception {
        saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelHold_success_returnsAvailable() throws Exception {
        // hold 취소 성공: seat 키 삭제, 마지막 좌석이므로 bundle 키도 삭제
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        long showId = 1L;

        String seatKey   = HoldKey.of(showId, seatId);
        String bundleKey = HoldKey.bundleOf(showId, user.getId());

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        // seat 키 삭제 확인
        Assertions.assertNull(stringRedisTemplate.opsForValue().get(seatKey));
        // 마지막 좌석이었으므로 bundle 키도 삭제 확인
        Long bundleSize = stringRedisTemplate.opsForSet().size(bundleKey);
        Assertions.assertTrue(bundleSize == null || bundleSize == 0L);
    }

    @Test
    void cancelHold_middleSeat_bundleKeepsRemainingMembers() throws Exception {
        // 3석 hold 후 그중 1석만 취소 → 취소한 seat 키만 삭제되고 bundle은 나머지 2석을 유지한 채 살아있어야 한다
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        long showId = 1L;
        List<Seat> seats = createSeats(showId, 3);
        String bundleKey = HoldKey.bundleOf(showId, user.getId());

        for (Seat seat : seats) {
            mockMvc.perform(post("/api/seats/{seatId}/hold", seat.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"showId": %d}
                                    """.formatted(showId)))
                    .andExpect(status().isOk());
        }

        Long canceledSeatId = seats.get(0).getId();
        String canceledSeatKey = HoldKey.of(showId, canceledSeatId);

        mockMvc.perform(delete("/api/seats/{seatId}/hold", canceledSeatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        // 취소한 좌석 키는 삭제됨
        Assertions.assertNull(stringRedisTemplate.opsForValue().get(canceledSeatKey));

        // bundle은 삭제되지 않고 나머지 2석을 그대로 유지
        Set<String> remainingMembers = stringRedisTemplate.opsForSet().members(bundleKey);
        Assertions.assertNotNull(remainingMembers);
        Assertions.assertEquals(2, remainingMembers.size());
        Assertions.assertFalse(remainingMembers.contains(String.valueOf(canceledSeatId)));
        Assertions.assertTrue(remainingMembers.contains(String.valueOf(seats.get(1).getId())));
        Assertions.assertTrue(remainingMembers.contains(String.valueOf(seats.get(2).getId())));

        // 남은 좌석들의 키는 그대로 유지
        Assertions.assertNotNull(stringRedisTemplate.opsForValue().get(HoldKey.of(showId, seats.get(1).getId())));
        Assertions.assertNotNull(stringRedisTemplate.opsForValue().get(HoldKey.of(showId, seats.get(2).getId())));

        // bundle 키 자체는 살아있어야 한다 (TTL 보유)
        Long bundleTtl = stringRedisTemplate.getExpire(bundleKey, TimeUnit.SECONDS);
        Assertions.assertNotNull(bundleTtl);
        Assertions.assertTrue(bundleTtl > 0);
    }

    @Test
    void cancelHold_notOwner_returns403() throws Exception {
        saveUser("user1@test.com");
        saveUser("user2@test.com");
        String token1 = loginAndGetToken("user1@test.com");
        String token2 = loginAndGetToken("user2@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        long showId = 1L;

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelHold_expired_returns409() throws Exception {
        saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        Seat seat = createSeat(1L, 1);
        Long seatId = seat.getId();
        long showId = 1L;

        String seatKey = HoldKey.of(showId, seatId);

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        stringRedisTemplate.expire(seatKey, 1, TimeUnit.SECONDS);
        Thread.sleep(1500);

        mockMvc.perform(delete("/api/seats/{seatId}/hold", seatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict());
    }

    @Test
    void hold_exceedsLimit_returns409() throws Exception {
        // 4석까지 hold 가능, 5번째 시도 → HOLD_LIMIT_EXCEEDED 409
        saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        long showId = 1L;
        List<Seat> seats = createSeats(showId, 5);

        for (int i = 0; i < 4; i++) {
            Long seatId = seats.get(i).getId();

            mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"showId": %d}
                                    """.formatted(showId)))
                    .andExpect(status().isOk());
        }

        Long seatId5 = seats.get(4).getId();

        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId5)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict());
    }

    @Test
    void hold_afterConfirmingFourSeats_newSessionCannotHoldMore_returns409() throws Exception {
        // 4석을 hold → confirm으로 확정 → 새 세션에서 다른 좌석을 hold해도 평생 상한(4석)에 걸려 실패해야 한다
        saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        long showId = 1L;
        List<Seat> seats = createSeats(showId, 5);
        List<Long> firstFour = seats.subList(0, 4).stream().map(Seat::getId).toList();

        for (Long seatId : firstFour) {
            mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"showId": %d}
                                    """.formatted(showId)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/reservations/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        Long fifthSeatId = seats.get(4).getId();

        mockMvc.perform(post("/api/seats/{seatId}/hold", fifthSeatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HOLD_LIMIT_EXCEEDED"));
    }

    @Test
    void hold_userAlreadyReservedTwoSeats_onlyTwoMoreCanBeHeldInNewSession() throws Exception {
        // 유저가 이 공연에서 이미 2석을 확정 예약한 상태 → 새 세션에서는 2석까지만 hold 가능, 3번째는 실패
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        long showId = 1L;
        List<Seat> seats = createSeats(showId, 5);

        for (int i = 0; i < 2; i++) {
            reservationRepository.save(
                    Reservation.builder()
                            .showId(showId)
                            .seatId(seats.get(i).getId())
                            .userId(user.getId())
                            .status(ReservationStatus.RESERVED)
                            .build()
            );
        }

        for (int i = 2; i < 4; i++) {
            mockMvc.perform(post("/api/seats/{seatId}/hold", seats.get(i).getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"showId": %d}
                                    """.formatted(showId)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/seats/{seatId}/hold", seats.get(4).getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HOLD_LIMIT_EXCEEDED"));
    }

    @Test
    void hold_userAlreadyReservedMaxSeats_newSessionCannotHoldAny_returns409() throws Exception {
        // 유저가 이 공연에서 이미 4석(상한)을 확정 예약한 상태 → 새 세션에서는 1석도 hold 불가
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        long showId = 1L;
        List<Seat> seats = createSeats(showId, 5);

        for (int i = 0; i < 4; i++) {
            reservationRepository.save(
                    Reservation.builder()
                            .showId(showId)
                            .seatId(seats.get(i).getId())
                            .userId(user.getId())
                            .status(ReservationStatus.RESERVED)
                            .build()
            );
        }

        mockMvc.perform(post("/api/seats/{seatId}/hold", seats.get(4).getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HOLD_LIMIT_EXCEEDED"));
    }

    @Test
    void hold_afterCancel_canHoldAgain() throws Exception {
        // 4석 hold 후 1석 cancel → 5번째 hold 성공
        saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        long showId = 1L;
        List<Seat> seats = createSeats(showId, 5);

        for (int i = 0; i < 4; i++) {
            Long seatId = seats.get(i).getId();

            mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"showId": %d}
                                    """.formatted(showId)))
                    .andExpect(status().isOk());
        }

        Long cancelSeatId = seats.get(0).getId();

        mockMvc.perform(delete("/api/seats/{seatId}/hold", cancelSeatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        Long newSeatId = seats.get(4).getId();

        mockMvc.perform(post("/api/seats/{seatId}/hold", newSeatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());
    }

    @Test
    void hold_multiSeat_bundleSizeGrows() throws Exception {
        // 좌석을 추가할수록 bundle SCARD가 증가하는지 확인
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        long showId = 1L;
        List<Seat> seats = createSeats(showId, 3);
        String bundleKey = HoldKey.bundleOf(showId, user.getId());

        for (int i = 0; i < 3; i++) {
            Long seatId = seats.get(i).getId();

            mockMvc.perform(post("/api/seats/{seatId}/hold", seatId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"showId": %d}
                                    """.formatted(showId)))
                    .andExpect(status().isOk());

            Long bundleSize = stringRedisTemplate.opsForSet().size(bundleKey);
            Assertions.assertEquals(i + 1, bundleSize);
        }
    }

    /**
     * 테스트 목적:
     * 서로 다른 20명의 사용자가 동시에 같은 좌석에 HOLD 요청을 보낼 때
     * Lua Script의 SET NX 원자성 덕분에 정확히 1명만 성공해야 한다.
     *
     * 기대 결과:
     * - HTTP 200 성공 응답 = 정확히 1건
     * - HTTP 409 충돌 응답 = 나머지 19건
     * - Redis seatKey에 owner가 1명만 기록됨
     */
    @Test
    void hold_concurrentSameSeats_onlyOneSucceeds() throws Exception {
        int threadCount = 20;
        long showId = 1L;
        Seat seat = createSeat(showId, 1);
        Long seatId = seat.getId();

        // 사용자 생성 및 토큰 발급은 순차적으로
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            saveUser("concurrent" + i + "@test.com");
            tokens.add(loginAndGetToken("concurrent" + i + "@test.com"));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount  = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (String token : tokens) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int status = mockMvc.perform(
                                    post("/api/seats/{seatId}/hold", seatId)
                                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("""
                                                    {"showId": %d}
                                                    """.formatted(showId))
                            )
                            .andReturn()
                            .getResponse()
                            .getStatus();

                    if (status == 200) successCount.incrementAndGet();
                    else if (status == 409) conflictCount.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 전 스레드 동시 출발
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 정확히 1명만 성공
        Assertions.assertEquals(1, successCount.get(), "hold 성공은 정확히 1건이어야 한다");
        Assertions.assertEquals(threadCount - 1, conflictCount.get());

        // Redis seatKey owner 확인
        String owner = stringRedisTemplate.opsForValue().get(HoldKey.of(showId, seatId));
        Assertions.assertNotNull(owner, "hold 성공한 사람의 seatKey가 Redis에 있어야 한다");
    }

    /**
     * 테스트 목적:
     * 같은 사용자가 10개의 서로 다른 좌석에 동시에 HOLD 요청을 보낼 때
     * Lua Script의 SCARD → SADD 원자적 실행 덕분에 bundle 최대 4석 제한이 깨지지 않아야 한다.
     *
     * 기대 결과:
     * - HTTP 200 성공 응답 = 정확히 4건
     * - HTTP 409 충돌 응답 = 나머지 6건 (HOLD_LIMIT_EXCEEDED)
     * - Redis bundle SCARD = 4
     */
    @Test
    void hold_concurrentExceedLimit_exactlyFourSucceed() throws Exception {
        int threadCount = 10;
        long showId = 1L;

        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");
        List<Seat> seats = createSeats(showId, threadCount);
        String bundleKey = HoldKey.bundleOf(showId, user.getId());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount      = new AtomicInteger(0);
        AtomicInteger limitExceededCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (Seat seat : seats) {
            Long seatId = seat.getId();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int status = mockMvc.perform(
                                    post("/api/seats/{seatId}/hold", seatId)
                                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("""
                                                    {"showId": %d}
                                                    """.formatted(showId))
                            )
                            .andReturn()
                            .getResponse()
                            .getStatus();

                    if (status == 200) successCount.incrementAndGet();
                    else if (status == 409) limitExceededCount.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 정확히 4석만 성공
        Assertions.assertEquals(4, successCount.get(), "bundle 최대 4석 제한이 지켜져야 한다");
        Assertions.assertEquals(threadCount - 4, limitExceededCount.get());

        // bundle SCARD 직접 확인
        Long bundleSize = stringRedisTemplate.opsForSet().size(bundleKey);
        Assertions.assertEquals(4L, bundleSize, "bundle에 정확히 4개의 seatId가 있어야 한다");
    }

    /**
     * 테스트 목적:
     * 두 번째 이후 좌석을 HOLD할 때 해당 seatKey의 TTL이
     * 새로운 300초가 아닌 bundle의 남은 TTL과 동기화되는지 확인한다.
     * Lua Script의 PTTL 분기(isFirstSeat=0 경로)가 실제로 동작하는지 검증한다.
     *
     * 기대 결과:
     * - 첫 번째 좌석 hold 후 bundle TTL을 150초로 강제 조정
     * - 두 번째 좌석 hold 시 seatKey TTL ≈ 150초 (5초 오차 허용)
     * - 300초가 아닌 남은 세션 시간에 맞춰져야 한다
     */
    @Test
    void hold_secondSeat_ttlSyncedToBundle() throws Exception {
        User user = saveUser("user@test.com");
        String token = loginAndGetToken("user@test.com");

        long showId = 1L;
        List<Seat> seats = createSeats(showId, 2);
        Long seatId1 = seats.get(0).getId();
        Long seatId2 = seats.get(1).getId();
        String bundleKey = HoldKey.bundleOf(showId, user.getId());
        String seat2Key  = HoldKey.of(showId, seatId2);

        // 첫 번째 좌석 hold (bundle TTL = 300s로 시작)
        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        // bundle TTL을 150초로 강제 조정 (남은 세션 시간을 임의로 줄임)
        stringRedisTemplate.expire(bundleKey, 150, TimeUnit.SECONDS);

        // 두 번째 좌석 hold
        mockMvc.perform(post("/api/seats/{seatId}/hold", seatId2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": %d}
                                """.formatted(showId)))
                .andExpect(status().isOk());

        // seat2 TTL이 bundle 남은 TTL(≈150s)에 맞춰졌는지 확인 (5초 오차 허용)
        Long seat2Ttl = stringRedisTemplate.getExpire(seat2Key, TimeUnit.SECONDS);
        Assertions.assertNotNull(seat2Ttl);
        Assertions.assertTrue(seat2Ttl >= 145 && seat2Ttl <= 150,
                "seat2 TTL(%ds)이 bundle 남은 TTL(≈150s)과 동기화되어야 한다".formatted(seat2Ttl));
    }
}
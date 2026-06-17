package com.demo.seatreservation.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.domain.enums.Role;
import com.demo.seatreservation.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AuthRefreshControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        Set<String> refreshKeys = stringRedisTemplate.keys("refresh:*");
        if (refreshKeys != null && !refreshKeys.isEmpty()) {
            stringRedisTemplate.delete(refreshKeys);
        }
    }

    private User saveUser(String email, String rawPassword, String name, String phone) {
        return userRepository.save(
                User.builder()
                        .email(email)
                        .password(passwordEncoder.encode(rawPassword))
                        .name(name)
                        .phone(phone)
                        .role(Role.USER)
                        .build()
        );
    }

    private LoginResult loginAndGetResult(String email, String password) throws Exception {
        String requestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String sessionId = root.get("data").get("sessionId").asText();
        String accessToken = root.get("data").get("accessToken").asText();
        String refreshToken = extractRefreshTokenFromCookie(result.getResponse().getHeader("Set-Cookie"));

        return new LoginResult(sessionId, accessToken, refreshToken);
    }

    @Test
    void refresh_noCookie_returns401() throws Exception {
        // 테스트 목적:
        // refresh cookie가 없으면 401 UNAUTHORIZED가 반환되어야 한다

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void refresh_success_reissuesAccessAndRefresh() throws Exception {
        // 테스트 목적:
        // refresh 성공 시 access token과 refresh token이
        // 모두 새로 발급되는지 확인

        User savedUser = saveUser("refresh@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult loginResult = loginAndGetResult("refresh@test.com", "12345678");

        MvcResult result = mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", loginResult.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.grantType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").isNumber())
                .andExpect(jsonPath("$.data.sessionId").value(loginResult.sessionId()))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String newAccessToken = root.get("data").get("accessToken").asText();
        String newRefreshToken = extractRefreshTokenFromCookie(result.getResponse().getHeader("Set-Cookie"));

        assertThat(newAccessToken).isNotBlank();
        assertThat(newRefreshToken).isNotBlank();
        assertThat(newRefreshToken).isNotEqualTo(loginResult.refreshToken());

        String refreshKey = "refresh:" + savedUser.getId() + ":" + loginResult.sessionId();
        String storedRefreshToken = stringRedisTemplate.opsForValue().get(refreshKey);
        assertThat(storedRefreshToken).isEqualTo(newRefreshToken);

        String lookupKey = "refresh:token:" + newRefreshToken;
        String metadata = stringRedisTemplate.opsForValue().get(lookupKey);
        assertThat(metadata).isEqualTo(savedUser.getId() + ":" + loginResult.sessionId());
    }

    @Test
    void refresh_oldRefreshTokenReuse_terminatesAllSessions() throws Exception {
        // 테스트 목적:
        // refresh rotation 후 이전 refresh token(ROTATED 마킹)을 재사용하면
        // 탈취로 간주하여 전체 세션을 종료하고 401을 반환해야 한다

        User savedUser = saveUser("reuse@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult loginResult = loginAndGetResult("reuse@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", loginResult.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        // 이전 토큰으로 재시도 → ROTATED 마킹 감지 → logoutAll 트리거
        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", loginResult.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));

        // logoutAll로 세션 전체 삭제됨
        String refreshKey = "refresh:" + savedUser.getId() + ":" + loginResult.sessionId();
        assertThat(stringRedisTemplate.opsForValue().get(refreshKey)).isNull();
        assertThat(stringRedisTemplate.hasKey("refresh:sessions:" + savedUser.getId())).isFalse();
    }

    @Test
    void refresh_redisMismatch_terminatesAllSessions() throws Exception {
        // 테스트 목적:
        // Redis에 저장된 refresh token 값과 쿠키 값이 다르면 탈취로 간주하여
        // 전체 세션을 종료하고 401을 반환해야 한다

        User savedUser = saveUser("mismatch@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult loginResult = loginAndGetResult("mismatch@test.com", "12345678");

        String refreshKey = "refresh:" + savedUser.getId() + ":" + loginResult.sessionId();
        stringRedisTemplate.opsForValue().set(refreshKey, "tampered-refresh-token");

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", loginResult.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));

        // logoutAll로 세션 전체 삭제됨
        assertThat(stringRedisTemplate.opsForValue().get(refreshKey)).isNull();
        assertThat(stringRedisTemplate.hasKey("refresh:sessions:" + savedUser.getId())).isFalse();
    }

    @Test
    void refresh_multipleDevices_independentSuccess() throws Exception {
        // 테스트 목적:
        // 같은 유저가 여러 기기에서 로그인한 경우
        // 각 refresh token이 독립적으로 동작해야 한다

        User savedUser = saveUser("device@test.com", "12345678", "홍길동", "010-1111-2222");

        LoginResult deviceA = loginAndGetResult("device@test.com", "12345678");
        LoginResult deviceB = loginAndGetResult("device@test.com", "12345678");

        assertThat(deviceA.sessionId()).isNotEqualTo(deviceB.sessionId());

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", deviceA.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(deviceA.sessionId()));

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", deviceB.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(deviceB.sessionId()));

        String refreshKeyA = "refresh:" + savedUser.getId() + ":" + deviceA.sessionId();
        String refreshKeyB = "refresh:" + savedUser.getId() + ":" + deviceB.sessionId();

        assertThat(stringRedisTemplate.hasKey(refreshKeyA)).isTrue();
        assertThat(stringRedisTemplate.hasKey(refreshKeyB)).isTrue();
    }

    @Test
    void refresh_cookieReissued_success() throws Exception {
        // 테스트 목적:
        // refresh 성공 시 새 refresh token이
        // Set-Cookie로 다시 내려가는지 확인

        saveUser("cookie@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult loginResult = loginAndGetResult("cookie@test.com", "12345678");

        MvcResult result = mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", loginResult.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth/refresh")))
                .andReturn();

        String newRefreshToken = extractRefreshTokenFromCookie(result.getResponse().getHeader("Set-Cookie"));
        assertThat(newRefreshToken).isNotBlank();
        assertThat(newRefreshToken).isNotEqualTo(loginResult.refreshToken());
    }

    @Test
    void refresh_success_oldLookupKeyMarkedAsRotated() throws Exception {
        // 테스트 목적:
        // refresh 성공 후 이전 토큰의 역조회 키가
        // "ROTATED:" 마킹으로 5분간 유지되는지 확인

        User savedUser = saveUser("rotated@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult loginResult = loginAndGetResult("rotated@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", loginResult.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk());

        String oldLookupKey = "refresh:token:" + loginResult.refreshToken();
        String markedValue = stringRedisTemplate.opsForValue().get(oldLookupKey);
        assertThat(markedValue).startsWith("ROTATED:");
        assertThat(markedValue).isEqualTo("ROTATED:" + savedUser.getId() + ":" + loginResult.sessionId());
    }

    @Test
    void refresh_rotatedTokenReuse_terminatesOtherDeviceSessions() throws Exception {
        // 테스트 목적:
        // ROTATED 마킹된 토큰 재사용 시 logoutAll이 호출되어
        // 다른 기기(핸드폰 등) 세션까지 모두 종료되어야 한다

        User savedUser = saveUser("attack@test.com", "12345678", "홍길동", "010-1111-2222");

        LoginResult deviceA = loginAndGetResult("attack@test.com", "12345678");
        LoginResult deviceB = loginAndGetResult("attack@test.com", "12345678");

        // deviceA refresh → tokenA가 ROTATED 마킹됨
        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", deviceA.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk());

        // deviceA 이전 토큰 재사용 → 탈취 감지 → logoutAll
        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", deviceA.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));

        // deviceA 세션 삭제
        String refreshKeyA = "refresh:" + savedUser.getId() + ":" + deviceA.sessionId();
        assertThat(stringRedisTemplate.opsForValue().get(refreshKeyA)).isNull();

        // deviceB 세션도 함께 삭제됨 (logoutAll)
        String refreshKeyB = "refresh:" + savedUser.getId() + ":" + deviceB.sessionId();
        assertThat(stringRedisTemplate.opsForValue().get(refreshKeyB)).isNull();

        // 세션 목록 자체도 삭제됨
        assertThat(stringRedisTemplate.hasKey("refresh:sessions:" + savedUser.getId())).isFalse();
    }

    private String extractRefreshTokenFromCookie(String setCookieHeader) {
        String[] parts = setCookieHeader.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("refreshToken=")) {
                return trimmed.substring("refreshToken=".length());
            }
        }
        return null;
    }

    private record LoginResult(
            String sessionId,
            String accessToken,
            String refreshToken
    ) {
    }
}
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
    void refresh_oldRefreshTokenReuse_returns401() throws Exception {
        // 테스트 목적:
        // refresh rotation 후 이전 refresh token을 다시 쓰면
        // 401 INVALID_REFRESH_TOKEN이 발생해야 한다

        User savedUser = saveUser("reuse@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult loginResult = loginAndGetResult("reuse@test.com", "12345678");

        MvcResult firstRefresh = mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", loginResult.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        String newRefreshToken = extractRefreshTokenFromCookie(firstRefresh.getResponse().getHeader("Set-Cookie"));

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", loginResult.refreshToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));

        String refreshKey = "refresh:" + savedUser.getId() + ":" + loginResult.sessionId();
        assertThat(stringRedisTemplate.opsForValue().get(refreshKey)).isEqualTo(newRefreshToken);
    }

    @Test
    void refresh_redisMismatch_returns401() throws Exception {
        // 테스트 목적:
        // Redis에 저장된 refresh token 값과
        // 쿠키 값이 다르면 401 INVALID_REFRESH_TOKEN이 발생해야 한다

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

        String sessionSetKey = "refresh:sessions:" + savedUser.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(sessionSetKey, loginResult.sessionId());
        assertThat(isMember).isFalse();
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
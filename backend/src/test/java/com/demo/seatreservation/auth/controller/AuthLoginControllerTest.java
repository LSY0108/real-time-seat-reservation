package com.demo.seatreservation.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.domain.enums.Role;
import com.demo.seatreservation.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
public class AuthLoginControllerTest {

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

        // 테스트 데이터 초기화
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

    @Test
    void login_success() throws Exception {

        // 테스트 목적:
        // 로그인 요청 시
        // access token, sessionId가 정상 응답되고 쿠키가 발급되는지 확인

        User savedUser = saveUser("test@test.com", "12345678", "홍길동", "010-1111-2222");

        String requestBody = """
                {
                  "email": "test@test.com",
                  "password": "12345678"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(savedUser.getId()))
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.grantType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").isNumber())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty());
    }

    @Test
    void login_userNotFound_returns401() throws Exception {

        // 테스트 목적:
        // 존재하지 않는 이메일로 로그인 시도하면
        // 401 INVALID_CREDENTIALS가 발생해야 한다

        String requestBody = """
                {
                  "email": "nouser@test.com",
                  "password": "12345678"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {

        // 테스트 목적:
        // 비밀번호가 일치하지 않으면
        // 401 INVALID_CREDENTIALS가 발생해야 한다

        saveUser("test@test.com", "12345678", "홍길동", "010-1111-2222");

        String requestBody = """
                {
                  "email": "test@test.com",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_invalidRequest_returns400() throws Exception {

        // 테스트 목적:
        // 로그인 요청값이 DTO 검증 조건에 맞지 않으면
        // 400 VALIDATION_ERROR가 발생해야 한다

        String requestBody = """
                {
                  "email": "not-email",
                  "password": ""
                }
                """;

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void login_accessTokenAndSessionId_success() throws Exception {

        // 테스트 목적:
        // 로그인 성공 시 access token과 sessionId가
        // 정상적으로 응답되는지 확인

        saveUser("token@test.com", "12345678", "홍길동", "010-1111-2222");

        String requestBody = """
                {
                  "email": "token@test.com",
                  "password": "12345678"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty());
    }

    @Test
    void login_refreshTokenSavedInRedis_success() throws Exception {

        // 테스트 목적:
        // 로그인 성공 시 Redis에
        // refresh:{userId}:{sessionId} = opaque refreshToken 이 저장되는지 확인

        User savedUser = saveUser("redis@test.com", "12345678", "홍길동", "010-1111-2222");

        String requestBody = """
                {
                  "email": "redis@test.com",
                  "password": "12345678"
                }
                """;

        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseBody);

        String sessionId = root.get("data").get("sessionId").asText();
        String refreshKey = "refresh:" + savedUser.getId() + ":" + sessionId;

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("refreshToken=");

        String refreshToken = extractRefreshTokenFromCookie(setCookie);
        String savedRefreshToken = stringRedisTemplate.opsForValue().get(refreshKey);

        assertThat(savedRefreshToken).isEqualTo(refreshToken);
    }

    @Test
    void login_sessionIdAddedToSet_success() throws Exception {

        // 테스트 목적:
        // 로그인 성공 시 refresh:sessions:{userId} Set에
        // sessionId가 추가되는지 확인

        User savedUser = saveUser("session@test.com", "12345678", "홍길동", "010-1111-2222");

        String requestBody = """
                {
                  "email": "session@test.com",
                  "password": "12345678"
                }
                """;

        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseBody);

        String sessionId = root.get("data").get("sessionId").asText();
        String sessionSetKey = "refresh:sessions:" + savedUser.getId();

        Boolean isMember = stringRedisTemplate.opsForSet().isMember(sessionSetKey, sessionId);

        assertThat(isMember).isTrue();
    }

    @Test
    void login_sameUserMultipleTimes_differentSessionId() throws Exception {

        // 테스트 목적:
        // 같은 유저가 여러 번 로그인해도
        // 서로 다른 sessionId가 각각 발급되고 저장되는지 확인

        User savedUser = saveUser("multi@test.com", "12345678", "홍길동", "010-1111-2222");

        String requestBody = """
                {
                  "email": "multi@test.com",
                  "password": "12345678"
                }
                """;

        MvcResult result1 = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andReturn();

        MvcResult result2 = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andReturn();

        String responseBody1 = result1.getResponse().getContentAsString();
        String responseBody2 = result2.getResponse().getContentAsString();

        JsonNode root1 = objectMapper.readTree(responseBody1);
        JsonNode root2 = objectMapper.readTree(responseBody2);

        String sessionId1 = root1.get("data").get("sessionId").asText();
        String sessionId2 = root2.get("data").get("sessionId").asText();

        assertThat(sessionId1).isNotEqualTo(sessionId2);

        String refreshKey1 = "refresh:" + savedUser.getId() + ":" + sessionId1;
        String refreshKey2 = "refresh:" + savedUser.getId() + ":" + sessionId2;

        assertThat(stringRedisTemplate.hasKey(refreshKey1)).isTrue();
        assertThat(stringRedisTemplate.hasKey(refreshKey2)).isTrue();
    }

    @Test
    void login_refreshTokenInCookieOnly() throws Exception {

        // 테스트 목적:
        // 로그인 성공 시 opaque refresh token은 body가 아니라
        // Set-Cookie 헤더로 내려가야 한다

        saveUser("cookie@test.com", "12345678", "홍길동", "010-1111-2222");

        String requestBody = """
                {
                  "email": "cookie@test.com",
                  "password": "12345678"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
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
}
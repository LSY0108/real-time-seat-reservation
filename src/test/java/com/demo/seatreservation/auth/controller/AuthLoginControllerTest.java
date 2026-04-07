package com.demo.seatreservation.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.repository.UserRepository;
import java.util.Set;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {

        // 테스트 데이터 초기화
        userRepository.deleteAll();

        // Redis 초기화
        Set<String> keys = redisTemplate.keys("refresh:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void login_success() throws Exception {

        // 테스트 목적:
        // 올바른 이메일/비밀번호로 로그인 시 성공 응답이 반환되는지 확인

        User savedUser = userRepository.save(
                User.builder()
                        .email("test@test.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("홍길동")
                        .phone("010-1111-2222")
                        .build()
        );

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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(savedUser.getId()))
                .andExpect(jsonPath("$.data.email").value("test@test.com"));
    }

    @Test
    void login_emailNotFound_returns401() throws Exception {

        // 테스트 목적:
        // 존재하지 않는 이메일로 로그인 시 401 INVALID_CREDENTIALS가 발생해야 한다

        String requestBody = """
                {
                  "email": "no-user@test.com",
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
        // 비밀번호 불일치 시 401 INVALID_CREDENTIALS가 발생해야 한다

        userRepository.save(
                User.builder()
                        .email("test@test.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("홍길동")
                        .phone("010-1111-2222")
                        .build()
        );

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
    void login_returnsAccessAndRefreshToken() throws Exception {

        // 테스트 목적:
        // 로그인 성공 시 accessToken, refreshToken, grantType이 응답에 포함되는지 확인

        userRepository.save(
                User.builder()
                        .email("token@test.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("홍길동")
                        .phone("010-1111-2222")
                        .build()
        );

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
                .andExpect(jsonPath("$.data.token.grantType").value("Bearer"))
                .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.token.refreshToken").isNotEmpty());
    }

    @Test
    void login_returnsSessionId() throws Exception {

        // 테스트 목적:
        // 로그인 성공 시 sessionId가 응답에 포함되는지 확인

        userRepository.save(
                User.builder()
                        .email("session@test.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("홍길동")
                        .phone("010-1111-2222")
                        .build()
        );

        String requestBody = """
                {
                  "email": "session@test.com",
                  "password": "12345678"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token.sessionId").isNotEmpty());
    }

    @Test
    void login_refreshTokenStoredInRedis_success() throws Exception {

        // 테스트 목적:
        // 로그인 성공 시 Redis의 refresh:{userId}:{sessionId} 키에
        // refreshToken이 저장되는지 확인

        User savedUser = userRepository.save(
                User.builder()
                        .email("redis@test.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("홍길동")
                        .phone("010-1111-2222")
                        .build()
        );

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

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        String sessionId = root.path("data").path("token").path("sessionId").asText();
        String refreshToken = root.path("data").path("token").path("refreshToken").asText();

        String redisKey = "refresh:" + savedUser.getId() + ":" + sessionId;
        String savedRefreshToken = redisTemplate.opsForValue().get(redisKey);

        assertThat(savedRefreshToken).isEqualTo(refreshToken);
    }

    @Test
    void login_sessionIdAddedToSessionSet_success() throws Exception {

        // 테스트 목적:
        // 로그인 성공 시 refresh:sessions:{userId} Set에
        // sessionId가 추가되는지 확인

        User savedUser = userRepository.save(
                User.builder()
                        .email("set@test.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("홍길동")
                        .phone("010-1111-2222")
                        .build()
        );

        String requestBody = """
                {
                  "email": "set@test.com",
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

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String sessionId = root.path("data").path("token").path("sessionId").asText();

        String sessionSetKey = "refresh:sessions:" + savedUser.getId();
        Set<String> sessionIds = redisTemplate.opsForSet().members(sessionSetKey);

        assertThat(sessionIds).isNotNull();
        assertThat(sessionIds).contains(sessionId);
    }

    @Test
    void login_multipleSessions_savedSeparately() throws Exception {

        // 테스트 목적:
        // 같은 유저가 여러 번 로그인해도
        // 서로 다른 sessionId로 각각 저장되는지 확인

        User savedUser = userRepository.save(
                User.builder()
                        .email("multi@test.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("홍길동")
                        .phone("010-1111-2222")
                        .build()
        );

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

        JsonNode root1 = objectMapper.readTree(result1.getResponse().getContentAsString());
        JsonNode root2 = objectMapper.readTree(result2.getResponse().getContentAsString());

        String sessionId1 = root1.path("data").path("token").path("sessionId").asText();
        String sessionId2 = root2.path("data").path("token").path("sessionId").asText();

        assertThat(sessionId1).isNotEqualTo(sessionId2);

        String sessionSetKey = "refresh:sessions:" + savedUser.getId();
        Set<String> sessionIds = redisTemplate.opsForSet().members(sessionSetKey);

        assertThat(sessionIds).isNotNull();
        assertThat(sessionIds).contains(sessionId1, sessionId2);
        assertThat(sessionIds.size()).isEqualTo(2);

        String refreshKey1 = "refresh:" + savedUser.getId() + ":" + sessionId1;
        String refreshKey2 = "refresh:" + savedUser.getId() + ":" + sessionId2;

        assertThat(redisTemplate.opsForValue().get(refreshKey1)).isNotBlank();
        assertThat(redisTemplate.opsForValue().get(refreshKey2)).isNotBlank();
    }
}
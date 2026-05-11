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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
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
public class AuthLogoutControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate stringRedisTemplate;
    @Autowired ObjectMapper objectMapper;

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
        String body = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String sessionId = root.get("data").get("sessionId").asText();
        String accessToken = root.get("data").get("accessToken").asText();
        String refreshToken = extractRefreshTokenFromCookie(result.getResponse().getHeader("Set-Cookie"));

        return new LoginResult(sessionId, accessToken, refreshToken);
    }

    @Test
    void logout_success_deletesRefreshTokenFromRedis() throws Exception {

        // 테스트 목적:
        // logout 성공 시 현재 세션의 refresh token이 Redis에서 삭제되어야 한다

        User user = saveUser("logout@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult login = loginAndGetResult("logout@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String refreshKey = "refresh:" + user.getId() + ":" + login.sessionId();
        assertThat(stringRedisTemplate.hasKey(refreshKey)).isFalse();

        String lookupKey = "refresh:token:" + login.refreshToken();
        assertThat(stringRedisTemplate.hasKey(lookupKey)).isFalse();
    }

    @Test
    void logout_removesSessionId_fromSessionSet() throws Exception {

        // 테스트 목적:
        // logout 성공 시 refresh:sessions:{userId} Set에서 sessionId가 제거되어야 한다
        User user = saveUser("session@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult login = loginAndGetResult("session@test.com", "12345678");

        String sessionSetKey = "refresh:sessions:" + user.getId();
        assertThat(stringRedisTemplate.opsForSet().isMember(sessionSetKey, login.sessionId())).isTrue();

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                )
                .andExpect(status().isOk());

        assertThat(stringRedisTemplate.opsForSet().isMember(sessionSetKey, login.sessionId())).isFalse();
    }

    @Test
    void logout_otherDeviceSession_preserved() throws Exception {

        // 테스트 목적:
        // A 기기 logout 시 B 기기의 refresh token은 삭제되지 않아야 한다
        User user = saveUser("multi@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult deviceA = loginAndGetResult("multi@test.com", "12345678");
        LoginResult deviceB = loginAndGetResult("multi@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA.accessToken())
                )
                .andExpect(status().isOk());

        String refreshKeyB = "refresh:" + user.getId() + ":" + deviceB.sessionId();
        assertThat(stringRedisTemplate.hasKey(refreshKeyB)).isTrue();

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", deviceB.refreshToken()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(deviceB.sessionId()));
    }

    @Test
    void logout_loggedOutSession_refreshTokenUnusable() throws Exception {

        // 테스트 목적:
        // logout한 세션의 refresh token은 이후 재사용할 수 없어야 한다
        saveUser("reuse@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult login = loginAndGetResult("reuse@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", login.refreshToken()))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_expiresRefreshCookie() throws Exception {

        // 테스트 목적:
        // logout 응답에 refresh cookie 만료 처리가 내려와야 한다
        saveUser("cookie@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult login = loginAndGetResult("cookie@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth/refresh")));
    }

    @Test
    void logout_noAuthorizationHeader_returns401() throws Exception {

        // 테스트 목적:
        // Authorization 헤더가 없으면 401 UNAUTHORIZED가 발생해야 한다
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void logout_invalidAccessToken_returns401() throws Exception {

        // 테스트 목적:
        // 유효하지 않은 access token으로 logout 시도 시 401 UNAUTHORIZED가 발생해야 한다
        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    private String extractRefreshTokenFromCookie(String setCookieHeader) {
        if (setCookieHeader == null) return null;
        for (String part : setCookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("refreshToken=")) {
                return trimmed.substring("refreshToken=".length());
            }
        }
        return null;
    }

    private record LoginResult(String sessionId, String accessToken, String refreshToken) {}
}

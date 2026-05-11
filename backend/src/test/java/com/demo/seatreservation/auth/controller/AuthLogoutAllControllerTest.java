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
public class AuthLogoutAllControllerTest {

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
    void logoutAll_success_deletesAllRefreshTokensFromRedis() throws Exception {

        // 테스트 목적:
        // logout-all 성공 시 해당 유저의 모든 세션 refresh token이 Redis에서 삭제되어야 한다

        User user = saveUser("all@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult deviceA = loginAndGetResult("all@test.com", "12345678");
        LoginResult deviceB = loginAndGetResult("all@test.com", "12345678");
        LoginResult deviceC = loginAndGetResult("all@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA.accessToken())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(stringRedisTemplate.hasKey("refresh:" + user.getId() + ":" + deviceA.sessionId())).isFalse();
        assertThat(stringRedisTemplate.hasKey("refresh:" + user.getId() + ":" + deviceB.sessionId())).isFalse();
        assertThat(stringRedisTemplate.hasKey("refresh:" + user.getId() + ":" + deviceC.sessionId())).isFalse();
    }

    @Test
    void logoutAll_success_deletesAllReverseLookupKeys() throws Exception {

        // 테스트 목적:
        // logout-all 성공 시 각 sessionId에 대응하는 refresh:token:{refreshToken} 역조회 키도 삭제되어야 한다

        saveUser("lookup@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult deviceA = loginAndGetResult("lookup@test.com", "12345678");
        LoginResult deviceB = loginAndGetResult("lookup@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA.accessToken())
                )
                .andExpect(status().isOk());

        assertThat(stringRedisTemplate.hasKey("refresh:token:" + deviceA.refreshToken())).isFalse();
        assertThat(stringRedisTemplate.hasKey("refresh:token:" + deviceB.refreshToken())).isFalse();
    }

    @Test
    void logoutAll_success_deletesSessionSet() throws Exception {

        // 테스트 목적:
        // logout-all 성공 시 refresh:sessions:{userId} Set이 삭제되어야 한다

        User user = saveUser("set@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult login = loginAndGetResult("set@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                )
                .andExpect(status().isOk());

        assertThat(stringRedisTemplate.hasKey("refresh:sessions:" + user.getId())).isFalse();
    }

    @Test
    void logoutAll_deviceA_refreshFails() throws Exception {

        // 테스트 목적:
        // logout-all 이후 A 기기의 refresh token으로 재발급을 시도하면 실패해야 한다

        saveUser("deva@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult deviceA = loginAndGetResult("deva@test.com", "12345678");
        loginAndGetResult("deva@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA.accessToken())
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", deviceA.refreshToken()))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutAll_deviceB_refreshFails() throws Exception {

        // 테스트 목적:
        // logout-all 이후 B 기기의 refresh token으로 재발급을 시도하면 실패해야 한다

        saveUser("devb@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult deviceA = loginAndGetResult("devb@test.com", "12345678");
        LoginResult deviceB = loginAndGetResult("devb@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA.accessToken())
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", deviceB.refreshToken()))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutAll_deviceC_refreshFails() throws Exception {

        // 테스트 목적:
        // logout-all 이후 C 기기의 refresh token으로 재발급을 시도하면 실패해야 한다

        saveUser("devc@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult deviceA = loginAndGetResult("devc@test.com", "12345678");
        LoginResult deviceB = loginAndGetResult("devc@test.com", "12345678");
        LoginResult deviceC = loginAndGetResult("devc@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA.accessToken())
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", deviceC.refreshToken()))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));

        // deviceB 추가 검증
        assertThat(stringRedisTemplate.hasKey("refresh:token:" + deviceB.refreshToken())).isFalse();
    }

    @Test
    void logoutAll_otherUserSession_preserved() throws Exception {

        // 테스트 목적:
        // logout-all 시 다른 사용자의 refresh token은 삭제되지 않아야 한다

        saveUser("user1@test.com", "12345678", "홍길동", "010-1111-2222");
        User user2 = saveUser("user2@test.com", "12345678", "김철수", "010-3333-4444");

        LoginResult user1Login = loginAndGetResult("user1@test.com", "12345678");
        LoginResult user2Login = loginAndGetResult("user2@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user1Login.accessToken())
                )
                .andExpect(status().isOk());

        String user2RefreshKey = "refresh:" + user2.getId() + ":" + user2Login.sessionId();
        assertThat(stringRedisTemplate.hasKey(user2RefreshKey)).isTrue();
        assertThat(stringRedisTemplate.hasKey("refresh:token:" + user2Login.refreshToken())).isTrue();

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("refreshToken", user2Login.refreshToken()))
                )
                .andExpect(status().isOk());
    }

    @Test
    void logoutAll_thenLogin_newSessionCreated() throws Exception {

        // 테스트 목적:
        // logout-all 이후 다시 로그인하면 새로운 session으로 정상 로그인 가능해야 한다

        User user = saveUser("relogin@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult before = loginAndGetResult("relogin@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + before.accessToken())
                )
                .andExpect(status().isOk());

        LoginResult after = loginAndGetResult("relogin@test.com", "12345678");

        assertThat(after.sessionId()).isNotEqualTo(before.sessionId());
        assertThat(stringRedisTemplate.hasKey("refresh:" + user.getId() + ":" + after.sessionId())).isTrue();
        assertThat(stringRedisTemplate.opsForSet().isMember("refresh:sessions:" + user.getId(), after.sessionId())).isTrue();
    }

    @Test
    void logoutAll_expiresRefreshCookie() throws Exception {

        // 테스트 목적:
        // logout-all 응답에 refresh cookie 만료 처리가 내려와야 한다

        saveUser("cookie@test.com", "12345678", "홍길동", "010-1111-2222");
        LoginResult login = loginAndGetResult("cookie@test.com", "12345678");

        mockMvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth/refresh")));
    }

    @Test
    void logoutAll_noAuthorizationHeader_returns401() throws Exception {

        // 테스트 목적:
        // Authorization 헤더가 없으면 401 UNAUTHORIZED가 발생해야 한다

        mockMvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void logoutAll_invalidAccessToken_returns401() throws Exception {

        // 테스트 목적:
        // 유효하지 않은 access token으로 logout-all 시도 시 401 UNAUTHORIZED가 발생해야 한다

        mockMvc.perform(
                        post("/api/auth/logout-all")
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
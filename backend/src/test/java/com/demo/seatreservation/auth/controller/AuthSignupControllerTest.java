package com.demo.seatreservation.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AuthSignupControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {

        // 테스트 데이터 초기화
        userRepository.deleteAll();
    }

    @Test
    void signup_success() throws Exception {

        // 테스트 목적:
        // 회원가입 요청 시
        // 정상적으로 회원이 저장되고 응답이 반환되는지 확인

        String requestBody = """
                {
                  "email": "test@test.com",
                  "password": "12345678",
                  "name": "홍길동",
                  "phone": "010-1111-2222"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.phone").value("010-1111-2222"));
    }

    @Test
    void signup_duplicatedEmail_returns409() throws Exception {

        // 테스트 목적:
        // 이미 가입된 이메일로 회원가입 시도하면
        // 409 EMAIL_DUPLICATED가 발생해야 한다

        userRepository.save(
                User.builder()
                        .email("dup@test.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("기존회원")
                        .phone("010-0000-0000")
                        .build()
        );

        String requestBody = """
                {
                  "email": "dup@test.com",
                  "password": "12345678",
                  "name": "새회원",
                  "phone": "010-1111-2222"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("EMAIL_DUPLICATED"));
    }

    @Test
    void signup_passwordEncoded_success() throws Exception {

        // 테스트 목적:
        // 회원가입 시 비밀번호가 평문이 아니라
        // 암호화되어 저장되는지 확인

        String rawPassword = "12345678";

        String requestBody = """
                {
                  "email": "encode@test.com",
                  "password": "12345678",
                  "name": "홍길동",
                  "phone": "010-1111-2222"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk());

        User savedUser = userRepository.findByEmail("encode@test.com")
                .orElseThrow();

        assert !savedUser.getPassword().equals(rawPassword);
        assert passwordEncoder.matches(rawPassword, savedUser.getPassword());
    }

    @Test
    void signup_invalidRequest_returns400() throws Exception {

        // 테스트 목적:
        // 회원가입 요청값이 DTO 검증 조건에 맞지 않으면
        // 400 VALIDATION_ERROR가 발생해야 한다

        String requestBody = """
                {
                  "email": "not-email",
                  "password": "123",
                  "name": "",
                  "phone": ""
                }
                """;

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
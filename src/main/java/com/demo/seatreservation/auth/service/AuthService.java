package com.demo.seatreservation.auth.service;

import com.demo.seatreservation.auth.dto.request.SignupRequest;
import com.demo.seatreservation.auth.dto.response.SignupResponse;
import com.demo.seatreservation.global.exception.BusinessException;
import com.demo.seatreservation.global.exception.ErrorCode;
import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional

public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupResponse signup(SignupRequest request) {

        // 1. 이메일 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        // 2. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 3. User 엔티티 생성
        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .name(request.getName())
                .phone(request.getPhone())
                .build();

        // 4. DB 저장
        User savedUser = userRepository.save(user);

        // 5. 응답 DTO 생성 (비밀번호 제외)
        return SignupResponse.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .name(savedUser.getName())
                .phone(savedUser.getPhone())
                .build();
    }
}

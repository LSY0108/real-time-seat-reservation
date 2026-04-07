package com.demo.seatreservation.auth.service;

import com.demo.seatreservation.auth.dto.request.LoginRequest;
import com.demo.seatreservation.auth.dto.request.SignupRequest;
import com.demo.seatreservation.auth.dto.response.LoginResponse;
import com.demo.seatreservation.auth.dto.response.SignupResponse;
import com.demo.seatreservation.auth.dto.response.TokenResponse;
import com.demo.seatreservation.auth.redis.RefreshTokenRedisRepository;
import com.demo.seatreservation.global.exception.BusinessException;
import com.demo.seatreservation.global.exception.ErrorCode;
import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.repository.UserRepository;
import com.demo.seatreservation.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;

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

    public LoginResponse login(LoginRequest request) {

        // 1. 이메일로 사용자 조회 (없으면 예외 발생)
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2. 비밀번호 검증 (일치하지 않으면 예외 발생)
        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 세션 ID 생성 (중복 방지를 위한 UUID 사용)
        String sessionId = UUID.randomUUID().toString();

        // 4. Access Token 생성 (사용자 정보 + 세선 ID 포함)
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                sessionId
        );

        // 5. Refresh Token 생성
        String refreshToken = jwtTokenProvider.createRefreshToken(
                user.getId(),
                sessionId
        );

        // 6. Refresh Token을 Redis에 저장 (만료 시간 포함)
        refreshTokenRedisRepository.save(
                user.getId(),
                sessionId,
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpireMs()
        );

        // 7. TokenResponse DTO 생성 (토큰 정보 구성)
        TokenResponse tokenResponse = TokenResponse.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(jwtTokenProvider.getAccessTokenExpireMs())
                .refreshTokenExpiresIn(jwtTokenProvider.getRefreshTokenExpireMs())
                .sessionId(sessionId)
                .build();

        // 8. LoginResponse 생성 및 반환
        return LoginResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .token(tokenResponse)
                .build();
    }
}

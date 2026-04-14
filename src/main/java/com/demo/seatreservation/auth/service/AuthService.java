package com.demo.seatreservation.auth.service;

import com.demo.seatreservation.auth.dto.request.LoginRequest;
import com.demo.seatreservation.auth.dto.request.SignupRequest;
import com.demo.seatreservation.auth.dto.response.*;
import com.demo.seatreservation.global.exception.BusinessException;
import com.demo.seatreservation.global.exception.ErrorCode;
import com.demo.seatreservation.domain.User;
import com.demo.seatreservation.repository.UserRepository;
import com.demo.seatreservation.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

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

    /**
     * 로그인
     *
     * - 이메일/비밀번호 검증
     * - sessionId 생성
     * - access / refresh token 생성
     * - Redis 에 refresh token 저장
     * - 웹 응답용 LoginResponse 반환
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 이메일로 사용자 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 세션 식별용 sessionId 생성
        String sessionId = UUID.randomUUID().toString();

        // 4. access / refresh token 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user, sessionId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), sessionId);

        // 5. Redis 에 refresh token 저장
        saveRefreshToken(user.getId(), sessionId, refreshToken);

        // 6. access token 응답 DTO 반환
        return LoginResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .grantType("Bearer")
                .accessToken(accessToken)
                .accessTokenExpiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .sessionId(sessionId)
                .build();
    }

    /**
     * 웹 응답에서는 refresh token 을 body 로 내리지 않지만,
     * 쿠키에 담기 위해 controller 에서 별도로 꺼내 쓸 수 있도록 제공
     */
    public String createRefreshToken(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String sessionId = UUID.randomUUID().toString();
        return jwtTokenProvider.generateRefreshToken(user.getId(), sessionId);
    }

    /**
     * 로그인 1회에 대해 access / refresh / sessionId가 반드시 동일 세트여야 하므로
     * controller 에서 refresh token을 따로 다시 만들지 않게
     * 내부 전용 메서드로 한 번에 생성/저장한다.
     */
    public LoginWithRefreshResult loginWithRefresh(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtTokenProvider.generateAccessToken(user, sessionId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), sessionId);

        saveRefreshToken(user.getId(), sessionId, refreshToken);

        LoginResponse loginResponse = LoginResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .grantType("Bearer")
                .accessToken(accessToken)
                .accessTokenExpiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .sessionId(sessionId)
                .build();

        return new LoginWithRefreshResult(loginResponse, refreshToken);
    }

    /**
     * Redis 저장 규칙
     *
     * refresh:{userId}:{sessionId} = refreshToken
     * refresh:sessions:{userId} = sessionId Set
     */
    private void saveRefreshToken(Long userId, String sessionId, String refreshToken) {
        String refreshKey = "refresh:" + userId + ":" + sessionId;
        String sessionSetKey = "refresh:sessions:" + userId;

        long refreshExpiration = jwtTokenProvider.getRefreshTokenExpiration();

        // refresh token 저장
        stringRedisTemplate.opsForValue()
                .set(refreshKey, refreshToken, Duration.ofMillis(refreshExpiration));

        // 세션 목록 Set 에 sessionId 추가
        stringRedisTemplate.opsForSet()
                .add(sessionSetKey, sessionId);

        // 세션 목록 Set 에도 만료 시간 설정
        stringRedisTemplate.expire(sessionSetKey, Duration.ofMillis(refreshExpiration));
    }

    public record LoginWithRefreshResult(
            LoginResponse loginResponse,
            String refreshToken
    ) {
    }
}

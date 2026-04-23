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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
     * - access token + refresh token을 한 번에 발급
     * - refresh token은 body가 아니라 cookie로 내려주기
     */
    public LoginWithRefreshResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 세션 단위로 refresh token을 구분하기 위한 값
        String sessionId = UUID.randomUUID().toString();

        // access token은 JWT로 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user, sessionId);

        // refresh token은 JWT가 아닌 랜덤 문자열(opaque token)로 발급
        String refreshToken = generateOpaqueRefreshToken();

        // Redis에 refresh token 저장
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
     * opaque refresh token 생성
     * - JWT 아님
     * - 충분히 긴 랜덤 문자열
     */
    private String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Redis 저장 규칙
     *
     * refresh:{userId}:{sessionId} = refreshToken
     * refresh:token:{refreshToken} = {userId}:{sessionId}
     * refresh:sessions:{userId} = sessionId Set
     *
     * refresh token 만료 시간과 세션 목록 만료 시간을 동일하게 맞춘다.
     */
    private void saveRefreshToken(Long userId, String sessionId, String refreshToken) {
        String refreshKey = "refresh:" + userId + ":" + sessionId;
        String refreshLookupKey = "refresh:token:" + refreshToken;
        String sessionSetKey = "refresh:sessions:" + userId;

        Duration ttl = Duration.ofMillis(refreshTokenExpirationMs);

        // 세션별 refresh token 저장
        stringRedisTemplate.opsForValue()
                .set(refreshKey, refreshToken, ttl);

        // refresh token 문자열로 userId/sessionId를 찾기 위한 역조회 키 저장
        stringRedisTemplate.opsForValue()
                .set(refreshLookupKey, userId + ":" + sessionId, ttl);

        // 해당 사용자의 세션 목록에 sessionId 추가
        stringRedisTemplate.opsForSet()
                .add(sessionSetKey, sessionId);

        // 세션 목록도 만료 시간 설정
        stringRedisTemplate.expire(sessionSetKey, ttl);
    }

    /**
     * Refresh Token 재발급
     * - 쿠키로 들어온 opaque refresh token을 Redis에서 역조회
     * - userId / sessionId 확인
     * - Redis에 저장된 값과 비교
     * - 성공 시 새 access token + 새 refresh token 발급(rotation)
     */
    public RefreshWithNewTokensResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // refresh token 문자열로 userId/sessionId 찾기
        String refreshLookupKey = "refresh:token:" + refreshToken;
        String refreshMetadata = stringRedisTemplate.opsForValue().get(refreshLookupKey);

        // Redis에 없으면 유효하지 않은 토큰으로 처리
        if (refreshMetadata == null || refreshMetadata.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // refreshMetadata 형식: "userId:sessionId"
        RefreshTokenContext context = parseRefreshMetadata(refreshMetadata);
        Long userId = context.userId();
        String sessionId = context.sessionId();

        String refreshKey = "refresh:" + userId + ":" + sessionId;
        String sessionSetKey = "refresh:sessions:" + userId;

        // Redis에 저장된 실제 refresh token과 비교
        String storedToken = stringRedisTemplate.opsForValue().get(refreshKey);

        // 값이 다르면 탈취 또는 위조 가능성으로 처리
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            deleteRefreshSession(refreshKey, refreshLookupKey, sessionSetKey, sessionId);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user, sessionId);
        String newRefreshToken = generateOpaqueRefreshToken();

        // 기존 토큰 삭제 후 새 토큰 저장 (rotation)
        stringRedisTemplate.delete(refreshLookupKey);
        saveRefreshToken(userId, sessionId, newRefreshToken);

        // access token만 응답 body로 내려주기 위한 DTO
        RefreshResponse refreshResponse = RefreshResponse.builder()
                .grantType("Bearer")
                .accessToken(newAccessToken)
                .accessTokenExpiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .sessionId(sessionId)
                .build();

        return new RefreshWithNewTokensResult(refreshResponse, newRefreshToken);
    }

    /**
     * Redis에 저장된 "userId:sessionId" 문자열을 분리해서 객체로 변환
     */
    private RefreshTokenContext parseRefreshMetadata(String metadata) {
        String[] parts = metadata.split(":", 2);
        if (parts.length != 2) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        try {
            Long userId = Long.valueOf(parts[0]);
            String sessionId = parts[1];
            return new RefreshTokenContext(userId, sessionId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    /**
     * 현재 세션 로그아웃
     * - JwtAuthenticationFilter가 검증한 Principal에서 userId / sessionId 수신
     * - Redis에서 해당 세션 refresh 데이터 전부 삭제
     */
    public void logout(Long userId, String sessionId) {
        String refreshKey = "refresh:" + userId + ":" + sessionId;

        String storedRefreshToken = stringRedisTemplate.opsForValue().get(refreshKey);
        if (storedRefreshToken != null) {
            stringRedisTemplate.delete("refresh:token:" + storedRefreshToken);
        }

        stringRedisTemplate.delete(refreshKey);
        stringRedisTemplate.opsForSet().remove("refresh:sessions:" + userId, sessionId);
    }

    /**
     * refresh token 검증 실패 시
     * 해당 세션의 refresh 관련 데이터 삭제
     */
    private void deleteRefreshSession(
            String refreshKey,
            String refreshLookupKey,
            String sessionSetKey,
            String sessionId
    ) {
        stringRedisTemplate.delete(refreshKey);
        stringRedisTemplate.delete(refreshLookupKey);
        stringRedisTemplate.opsForSet().remove(sessionSetKey, sessionId);
    }

    /**
     * 로그인 결과와 refresh token을 함께 반환하기 위한 내부 record
     */
    public record LoginWithRefreshResult(
            LoginResponse loginResponse,
            String refreshToken
    ) {
    }

    /**
     * refresh 결과와 새 refresh token을 함께 반환하기 위한 내부 record
     */
    public record RefreshWithNewTokensResult(
            RefreshResponse refreshResponse,
            String newRefreshToken
    ) {
    }

    /**
     * refresh token 역조회 결과를 담는 내부 record
     */
    private record RefreshTokenContext(
            Long userId,
            String sessionId
    ) {
    }
}

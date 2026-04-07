package com.demo.seatreservation.auth.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private String refreshKey(Long userId, String sessionId) {
        return "refresh:" + userId + ":" + sessionId;
    }

    private String sessionSetKey(Long userId) {
        return "refresh:sessions:" + userId;
    }

    /**
    * 특정 사용자 세션의 Refresh Token을 저장하고, 세션 목록 Set에도 sessionId를 추가한다.
    * */
    public void save(Long userId, String sessionId, String refreshToken, long refreshTokenExpireMs) {
        redisTemplate.opsForValue().set(
                refreshKey(userId, sessionId),
                refreshToken,
                Duration.ofMillis(refreshTokenExpireMs)
        );

        redisTemplate.opsForSet().add(sessionSetKey(userId), sessionId);
    }

    /**
     * 특정 사용자 세션에 저장된 Refresh Token을 조회한다.
     * */
    public String find(Long userId, String sessionId) {
        return redisTemplate.opsForValue().get(refreshKey(userId, sessionId));
    }

    /**
     * 현재 세션의 Refresh Token을 삭제하고,세션 목록 Set에서도 sessionId를 제거한다.
     * */
    public void deleteCurrentSession(Long userId, String sessionId) {
        redisTemplate.delete(refreshKey(userId, sessionId));
        redisTemplate.opsForSet().remove(sessionSetKey(userId), sessionId);
    }

    /**
     * 해당 사용자의 활성 sessionId 목록을 조회한다.
     * */
    public Set<String> findSessionIds(Long userId) {
        Set<String> values = redisTemplate.opsForSet().members(sessionSetKey(userId));
        return values == null ? Collections.emptySet() : values;
    }

    /**
     * 해당 사용자의 모든 세션 Refresh Token을 삭제하고, 세션 목록 Set도 함께 제거한다.
     * */
    public void deleteAllSessions(Long userId) {
        Set<String> sessionIds = findSessionIds(userId);
        for (String sessionId : sessionIds) {
            redisTemplate.delete(refreshKey(userId, sessionId));
        }
        redisTemplate.delete(sessionSetKey(userId));
    }
}
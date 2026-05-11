package com.demo.seatreservation.seat.redis;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HoldRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public HoldRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // NX + TTL
    public boolean tryHold(String key, String userId, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, userId, ttl);
        return Boolean.TRUE.equals(ok);
    }

    public long getTtlSec(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? -2L : ttl;
    }

    public String getOwner(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // 사용자 HOLD 개수 조회
    public Long getUserHoldCount(String userHoldKey) {
        return redisTemplate.opsForSet().size(userHoldKey);
    }

    // 사용자 HOLD 추가
    public void addUserHold(String userHoldKey, Long seatId, Duration ttl) {
        redisTemplate.opsForSet().add(userHoldKey, String.valueOf(seatId));
        redisTemplate.expire(userHoldKey, ttl);
    }

    // 사용자 HOLD 제거
    public void removeUserHold(String userHoldKey, Long seatId) {
        redisTemplate.opsForSet().remove(userHoldKey, String.valueOf(seatId));
    }

}

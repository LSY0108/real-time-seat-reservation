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
}

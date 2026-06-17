package com.demo.seatreservation.seat.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
public class HoldRedisRepository {

    // 결과 규약: -1 = HOLD_LIMIT_EXCEEDED, -2 = SEAT_ALREADY_HELD, -3 = 예기치 않은 상태, >=0 = 성공(잔여 TTL 초)
    private static final DefaultRedisScript<Long> TRY_HOLD_SCRIPT;

    static {
        TRY_HOLD_SCRIPT = new DefaultRedisScript<>();
        TRY_HOLD_SCRIPT.setResultType(Long.class);
        TRY_HOLD_SCRIPT.setScriptText("""
                local bundleKey   = KEYS[1]
                local seatKey     = KEYS[2]
                local userId      = ARGV[1]
                local seatId      = ARGV[2]
                local maxSeats    = tonumber(ARGV[3])
                local bundleTtlSec = tonumber(ARGV[4])

                local pttl = redis.call('PTTL', bundleKey)
                local seatTtlMs
                local isFirstSeat

                if pttl == -2 then
                    seatTtlMs    = bundleTtlSec * 1000
                    isFirstSeat  = 1
                elseif pttl > 0 then
                    seatTtlMs    = pttl
                    isFirstSeat  = 0
                else
                    return -3
                end

                local count = redis.call('SCARD', bundleKey)
                if count >= maxSeats then
                    return -1
                end

                local ok = redis.call('SET', seatKey, userId, 'PX', seatTtlMs, 'NX')
                if not ok then
                    return -2
                end

                redis.call('SADD', bundleKey, seatId)

                if isFirstSeat == 1 then
                    redis.call('EXPIRE', bundleKey, bundleTtlSec)
                end

                return math.floor(seatTtlMs / 1000)
                """);
    }

    private final RedisTemplate<String, String> redisTemplate;

    public HoldRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Lua 스크립트로 PTTL → SCARD → SET NX PX → SADD → (EXPIRE) 를 원자적으로 실행한다.
     * 반환값: -1(4석 초과), -2(좌석 이미 선점됨), -3(예기치 않은 상태), >=0(성공, 잔여 TTL 초)
     */
    public long executeTryHold(String bundleKey, String seatKey,
                               String userId, String seatId, long bundleTtlSec) {
        Long result = redisTemplate.execute(
                TRY_HOLD_SCRIPT,
                List.of(bundleKey, seatKey),
                userId, seatId, "4", String.valueOf(bundleTtlSec)
        );
        return result == null ? -3L : result;
    }

    /** bundle 잔여 TTL(ms). 키 없음 → -2, TTL 없음 → -1 */
    public long getBundleRemainingTtlMs(String bundleKey) {
        Long ttl = redisTemplate.getExpire(bundleKey, TimeUnit.MILLISECONDS);
        return ttl == null ? -2L : ttl;
    }

    public Set<String> getBundleSeatIds(String bundleKey) {
        return redisTemplate.opsForSet().members(bundleKey);
    }

    public Long getBundleSize(String bundleKey) {
        return redisTemplate.opsForSet().size(bundleKey);
    }

    public void removeFromBundle(String bundleKey, Long seatId) {
        redisTemplate.opsForSet().remove(bundleKey, String.valueOf(seatId));
    }

    public void deleteBundle(String bundleKey) {
        redisTemplate.delete(bundleKey);
    }

    public String getOwner(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public long getTtlSec(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? -2L : ttl;
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
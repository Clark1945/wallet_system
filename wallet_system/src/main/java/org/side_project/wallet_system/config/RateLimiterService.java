package org.side_project.wallet_system.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Fixed-window rate limiter backed by Redis.
     * Fails open if Redis is unavailable to avoid blocking legitimate traffic.
     *
     * @return true if the request is within the allowed limit, false if exceeded
     */
    public boolean isAllowed(String key, int maxRequests, Duration window) {
        String redisKey = "rate_limit:" + key;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count == null) return true;
            if (count == 1) {
                stringRedisTemplate.expire(redisKey, window);
            }
            return count <= maxRequests;
        } catch (Exception e) {
            log.warn("Rate limiter Redis error, failing open: key={}", redisKey, e);
            return true;
        }
    }
}
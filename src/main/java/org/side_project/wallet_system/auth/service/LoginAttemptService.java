package org.side_project.wallet_system.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate stringRedisTemplate;

    static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW_TTL = Duration.ofMinutes(15);

    public void recordFailure(String email) {
        String attemptsKey = "login_attempts:" + email;
        Long count = stringRedisTemplate.opsForValue().increment(attemptsKey);
        if (count == null) count = (long) MAX_ATTEMPTS;
        if (count == 1) {
            stringRedisTemplate.expire(attemptsKey, WINDOW_TTL);
        }
        if (count >= MAX_ATTEMPTS) {
            stringRedisTemplate.opsForValue().set("login_lock:" + email, "1", WINDOW_TTL);
            stringRedisTemplate.delete(attemptsKey);
            log.warn("Account locked after {} failed attempts: email={}", MAX_ATTEMPTS, email);
        } else {
            log.debug("Login failure recorded: email={}, attempt={}/{}", email, count, MAX_ATTEMPTS);
        }
    }

    public boolean isLocked(String email) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey("login_lock:" + email));
    }

    public void clearFailures(String email) {
        stringRedisTemplate.delete("login_attempts:" + email);
        stringRedisTemplate.delete("login_lock:" + email);
    }
}

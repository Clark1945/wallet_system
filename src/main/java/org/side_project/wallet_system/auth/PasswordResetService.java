package org.side_project.wallet_system.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final StringRedisTemplate redis;
    private static final Duration RESET_TTL = Duration.ofMinutes(15);

    public String generateToken(UUID memberId) {
        String token = UUID.randomUUID().toString();
        String key = buildKey(memberId, token);
        redis.opsForValue().set(key, "", RESET_TTL);
        log.debug("Password reset token stored: memberId={}", memberId);
        return token;
    }

    public boolean isValid(UUID memberId, String token) {
        return Boolean.TRUE.equals(redis.hasKey(buildKey(memberId, token)));
    }

    public boolean verify(UUID memberId, String token) {
        Boolean deleted = redis.delete(buildKey(memberId, token));
        boolean valid = Boolean.TRUE.equals(deleted);
        log.debug("Password reset verify: memberId={}, valid={}", memberId, valid);
        return valid;
    }

    private String buildKey(UUID memberId, String token) {
        return "member:" + memberId + ":passwordResetToken:" + token;
    }
}

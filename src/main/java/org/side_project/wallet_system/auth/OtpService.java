package org.side_project.wallet_system.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redis;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(10);

    public String generateAndStore(UUID memberId, OtpType type) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = buildKey(memberId, type, otp);
        redis.opsForValue().set(key, "", OTP_TTL);
        log.debug("OTP stored: type={}, memberId={}", type, memberId);
        return otp;
    }

    public boolean verify(UUID memberId, OtpType type, String code) {
        String key = buildKey(memberId, type, code);
        Boolean deleted = redis.delete(key);
        boolean valid = Boolean.TRUE.equals(deleted);
        log.debug("OTP verify: type={}, memberId={}, valid={}", type, memberId, valid);
        return valid;
    }

    private String buildKey(UUID memberId, OtpType type, String token) {
        String suffix = switch (type) {
            case LOGIN    -> "loginToken";
            case REGISTER -> "registerToken";
        };
        return "member:" + memberId + ":" + suffix + ":" + token;
    }
}

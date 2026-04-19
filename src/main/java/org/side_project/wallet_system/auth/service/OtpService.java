package org.side_project.wallet_system.auth.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.auth.objects.OtpType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String,Object> redisTemplate;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(10);

    public String generateAndStore(UUID memberId, OtpType type) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = buildKey(memberId, type, otp);
        stringRedisTemplate.opsForValue().set(key, "", OTP_TTL);
        log.debug("OTP stored: type={}, memberId={}", type, memberId);
        return otp;
    }

    public boolean verify(UUID memberId, OtpType type, String code) {
        String key = buildKey(memberId, type, code);
        Boolean deleted = stringRedisTemplate.delete(key);
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

    public String generateOtpToken(UUID memberId, OtpType type) {
        String token = UUID.randomUUID().toString();

        OtpToken otpToken = new OtpToken();
        otpToken.setToken(token);
        otpToken.setMemberId(memberId);
        otpToken.setType(type);
        otpToken.setExpiredAt(LocalDateTime.now().plusMinutes(5));

        String redisKey = "otp_token:" + token;
        redisTemplate.opsForValue().set(redisKey, otpToken, 5, TimeUnit.MINUTES);

        return token;
    }

    public UUID resolveOtpToken(String token, OtpType type) {
        String redisKey = "otp_token:" + token;

        OtpToken otpToken = (OtpToken) redisTemplate.opsForValue().get(redisKey);
        if (otpToken == null) {
            throw new IllegalArgumentException("error.otp.invalid");
        }
        if (!otpToken.getType().equals(type) ||
                otpToken.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("error.otp.expired");
        }

        return otpToken.getMemberId();
    }

    public boolean consumeToken(String token) {
        String redisKey = "otp_token:" + token;

        return redisTemplate.delete(redisKey);
    }

    @Setter
    @Getter
    static class OtpToken {

        private String token;
        private UUID memberId;
        private OtpType type;
        private LocalDateTime expiredAt;
    }
}

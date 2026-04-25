package org.side_project.wallet_system.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.internal.dto.PaymentTokenData;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTokenService {

    private static final String KEY_PREFIX = "payment_token:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final RedisTemplate<String, Object> redisTemplate;

    public String createToken(UUID memberId, BigDecimal amount, String method, String notifyEmail) {
        String token = UUID.randomUUID().toString();
        PaymentTokenData data = new PaymentTokenData(memberId, amount, method, notifyEmail);
        redisTemplate.opsForValue().set(KEY_PREFIX + token, data, TTL);
        log.debug("Payment token created: token={}, memberId={}, method={}", token, memberId, method);
        return token;
    }

    public PaymentTokenData validateAndConsumeToken(String token) {
        String key = KEY_PREFIX + token;
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw == null) {
            log.warn("Payment token not found or expired: token={}", token);
            return null;
        }
        redisTemplate.delete(key);
        if (raw instanceof PaymentTokenData data) {
            return data;
        }
        // RedisTemplate deserializes JSON as LinkedHashMap when type info is absent
        if (raw instanceof java.util.LinkedHashMap<?, ?> map) {
            UUID memberId = UUID.fromString((String) map.get("memberId"));
            BigDecimal amount = new BigDecimal(map.get("amount").toString());
            String method = (String) map.get("method");
            String notifyEmail = (String) map.get("notifyEmail");
            return new PaymentTokenData(memberId, amount, method, notifyEmail);
        }
        log.error("Unexpected type in Redis for payment token: {}", raw.getClass());
        return null;
    }
}

package org.side_project.wallet_system.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.internal.dto.PaymentTokenData;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTokenServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @InjectMocks private PaymentTokenService paymentTokenService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ── createToken ───────────────────────────────────────────

    @Test
    void createToken_returnsToken_andStoresInRedis() {
        UUID memberId = UUID.randomUUID();

        String token = paymentTokenService.createToken(memberId, new BigDecimal("100.00"), "stripe", "notify@example.com");

        assertThat(token).isNotBlank();
        then(valueOps).should().set(
            eq("payment_token:" + token),
            argThat(obj -> obj instanceof PaymentTokenData pd
                && pd.memberId().equals(memberId)
                && pd.amount().compareTo(new BigDecimal("100.00")) == 0
                && "stripe".equals(pd.method())
                && "notify@example.com".equals(pd.notifyEmail())),
            eq(Duration.ofMinutes(15))
        );
    }

    @Test
    void createToken_withNullEmail_storesNullNotifyEmail() {
        UUID memberId = UUID.randomUUID();

        String token = paymentTokenService.createToken(memberId, new BigDecimal("50.00"), "sbpayment", null);

        assertThat(token).isNotBlank();
        then(valueOps).should().set(
            eq("payment_token:" + token),
            argThat(obj -> obj instanceof PaymentTokenData pd && pd.notifyEmail() == null),
            any()
        );
    }

    // ── validateAndConsumeToken — direct type ─────────────────

    @Test
    void validateAndConsumeToken_directType_returnsData() {
        String token = "direct-token";
        UUID memberId = UUID.randomUUID();
        PaymentTokenData stored = new PaymentTokenData(memberId, new BigDecimal("200.00"), "stripe", null);
        given(valueOps.get("payment_token:" + token)).willReturn(stored);

        PaymentTokenData result = paymentTokenService.validateAndConsumeToken(token);

        assertThat(result).isNotNull();
        assertThat(result.memberId()).isEqualTo(memberId);
        assertThat(result.amount()).isEqualByComparingTo("200.00");
        assertThat(result.method()).isEqualTo("stripe");
        then(redisTemplate).should().delete("payment_token:" + token);
    }

    // ── validateAndConsumeToken — LinkedHashMap (Redis deserialisation) ─────

    @Test
    void validateAndConsumeToken_linkedHashMap_deserializesCorrectly() {
        String token = "map-token";
        UUID memberId = UUID.randomUUID();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("memberId", memberId.toString());
        map.put("amount", "150.50");
        map.put("method", "sbpayment");
        map.put("notifyEmail", "user@example.com");
        given(valueOps.get("payment_token:" + token)).willReturn(map);

        PaymentTokenData result = paymentTokenService.validateAndConsumeToken(token);

        assertThat(result).isNotNull();
        assertThat(result.memberId()).isEqualTo(memberId);
        assertThat(result.amount()).isEqualByComparingTo("150.50");
        assertThat(result.method()).isEqualTo("sbpayment");
        assertThat(result.notifyEmail()).isEqualTo("user@example.com");
        then(redisTemplate).should().delete("payment_token:" + token);
    }

    @Test
    void validateAndConsumeToken_linkedHashMap_nullNotifyEmail_deserializes() {
        String token = "map-null-email";
        UUID memberId = UUID.randomUUID();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("memberId", memberId.toString());
        map.put("amount", "99.00");
        map.put("method", "stripe");
        map.put("notifyEmail", null);
        given(valueOps.get("payment_token:" + token)).willReturn(map);

        PaymentTokenData result = paymentTokenService.validateAndConsumeToken(token);

        assertThat(result).isNotNull();
        assertThat(result.notifyEmail()).isNull();
    }

    // ── validateAndConsumeToken — missing / unexpected ────────

    @Test
    void validateAndConsumeToken_notFound_returnsNull() {
        given(valueOps.get(any())).willReturn(null);

        PaymentTokenData result = paymentTokenService.validateAndConsumeToken("ghost-token");

        assertThat(result).isNull();
        then(redisTemplate).should(never()).delete(anyString());
    }

    @Test
    void validateAndConsumeToken_unexpectedType_returnsNull() {
        given(valueOps.get(any())).willReturn("unexpected-string");

        PaymentTokenData result = paymentTokenService.validateAndConsumeToken("weird-token");

        assertThat(result).isNull();
        then(redisTemplate).should().delete("payment_token:weird-token"); // still deleted
    }
}

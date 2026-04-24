package org.side_project.wallet_system.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks RateLimiterService service;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ─── first request ────────────────────────────────────────────────────────

    @Test
    void isAllowed_firstRequest_setsWindowExpiryAndReturnsTrue() {
        given(valueOps.increment("rate_limit:login:1.2.3.4")).willReturn(1L);

        boolean result = service.isAllowed("login:1.2.3.4", 10, Duration.ofSeconds(60));

        assertThat(result).isTrue();
        then(stringRedisTemplate).should().expire(eq("rate_limit:login:1.2.3.4"), eq(Duration.ofSeconds(60)));
    }

    // ─── subsequent requests ──────────────────────────────────────────────────

    @Test
    void isAllowed_subsequentRequest_doesNotResetExpiry() {
        given(valueOps.increment(anyString())).willReturn(5L);

        service.isAllowed("login:1.2.3.4", 10, Duration.ofSeconds(60));

        then(stringRedisTemplate).should(never()).expire(any(), any());
    }

    @Test
    void isAllowed_atLimit_returnsTrue() {
        given(valueOps.increment(anyString())).willReturn(10L);

        assertThat(service.isAllowed("login:1.2.3.4", 10, Duration.ofSeconds(60))).isTrue();
    }

    @Test
    void isAllowed_exceedsLimit_returnsFalse() {
        given(valueOps.increment(anyString())).willReturn(11L);

        assertThat(service.isAllowed("login:1.2.3.4", 10, Duration.ofSeconds(60))).isFalse();
    }

    // ─── failure modes ────────────────────────────────────────────────────────

    @Test
    void isAllowed_redisUnavailable_failsOpenReturnsTrue() {
        given(valueOps.increment(anyString())).willThrow(new RuntimeException("Redis connection refused"));

        assertThat(service.isAllowed("login:1.2.3.4", 10, Duration.ofSeconds(60))).isTrue();
    }

    @Test
    void isAllowed_nullCountFromRedis_failsOpenReturnsTrue() {
        given(valueOps.increment(anyString())).willReturn(null);

        assertThat(service.isAllowed("login:1.2.3.4", 10, Duration.ofSeconds(60))).isTrue();
    }
}

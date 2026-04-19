package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks LoginAttemptService service;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ─── recordFailure ────────────────────────────────────────────────────────

    @Test
    void recordFailure_firstAttempt_setsWindowExpiry() {
        given(valueOps.increment("login_attempts:alice@example.com")).willReturn(1L);

        service.recordFailure("alice@example.com");

        then(stringRedisTemplate).should().expire(eq("login_attempts:alice@example.com"), any(Duration.class));
        then(valueOps).should(never()).set(contains("login_lock"), any(), any(Duration.class));
    }

    @Test
    void recordFailure_secondAttempt_doesNotResetExpiryOrLock() {
        given(valueOps.increment("login_attempts:alice@example.com")).willReturn(2L);

        service.recordFailure("alice@example.com");

        then(stringRedisTemplate).should(never()).expire(any(), any());
        then(valueOps).should(never()).set(contains("login_lock"), any(), any(Duration.class));
    }

    @Test
    void recordFailure_fifthAttempt_setsLockAndDeletesAttemptCounter() {
        given(valueOps.increment("login_attempts:alice@example.com")).willReturn(5L);

        service.recordFailure("alice@example.com");

        then(valueOps).should().set(eq("login_lock:alice@example.com"), eq("1"), any(Duration.class));
        then(stringRedisTemplate).should().delete("login_attempts:alice@example.com");
    }

    // ─── isLocked ─────────────────────────────────────────────────────────────

    @Test
    void isLocked_whenLockKeyExists_returnsTrue() {
        given(stringRedisTemplate.hasKey("login_lock:locked@example.com")).willReturn(true);

        assertThat(service.isLocked("locked@example.com")).isTrue();
    }

    @Test
    void isLocked_whenNoLockKey_returnsFalse() {
        given(stringRedisTemplate.hasKey("login_lock:free@example.com")).willReturn(false);

        assertThat(service.isLocked("free@example.com")).isFalse();
    }

    // ─── clearFailures ────────────────────────────────────────────────────────

    @Test
    void clearFailures_deletesBothAttemptsAndLockKeys() {
        service.clearFailures("user@example.com");

        then(stringRedisTemplate).should().delete("login_attempts:user@example.com");
        then(stringRedisTemplate).should().delete("login_lock:user@example.com");
    }
}

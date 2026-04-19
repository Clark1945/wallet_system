package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.auth.service.PasswordResetService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks PasswordResetService service;

    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ─── generateToken ────────────────────────────────────────────────────────

    @Test
    void generateToken_returnsUuidFormatToken() {
        String token = service.generateToken(memberId);

        assertThat(token).isNotBlank();
        assertThatCode(() -> UUID.fromString(token)).doesNotThrowAnyException();
    }

    @Test
    void generateToken_storesExpectedKeyInRedis() {
        String token = service.generateToken(memberId);

        String expectedKey = "member:" + memberId + ":passwordResetToken:" + token;
        then(valueOps).should().set(eq(expectedKey), eq(""), any(Duration.class));
    }

    // ─── isValid ──────────────────────────────────────────────────────────────

    @Test
    void isValid_whenKeyExists_returnsTrue() {
        given(redis.hasKey(anyString())).willReturn(true);

        assertThat(service.isValid(memberId, "some-token")).isTrue();
    }

    @Test
    void isValid_whenKeyMissing_returnsFalse() {
        given(redis.hasKey(anyString())).willReturn(false);

        assertThat(service.isValid(memberId, "some-token")).isFalse();
    }

    @Test
    void isValid_checksCorrectRedisKey() {
        String token = "test-reset-token";
        given(redis.hasKey(anyString())).willReturn(true);

        service.isValid(memberId, token);

        then(redis).should().hasKey("member:" + memberId + ":passwordResetToken:" + token);
    }

    // ─── verify ───────────────────────────────────────────────────────────────

    @Test
    void verify_whenKeyDeleted_returnsTrue() {
        given(redis.delete(anyString())).willReturn(true);

        assertThat(service.verify(memberId, "valid-token")).isTrue();
    }

    @Test
    void verify_whenKeyNotFound_returnsFalse() {
        given(redis.delete(anyString())).willReturn(false);

        assertThat(service.verify(memberId, "expired-token")).isFalse();
    }

    @Test
    void verify_deletesCorrectRedisKey() {
        String token = "my-reset-token";
        given(redis.delete(anyString())).willReturn(true);

        service.verify(memberId, token);

        then(redis).should().delete("member:" + memberId + ":passwordResetToken:" + token);
    }
}

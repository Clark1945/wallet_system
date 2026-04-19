package org.side_project.wallet_system.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.auth.objects.OtpType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, String> stringOps;
    @Mock ValueOperations<String, Object> objectOps;

    // Manually instantiate to avoid Mockito mis-injecting due to type erasure
    // (StringRedisTemplate IS-A RedisTemplate, causing @InjectMocks ambiguity)
    OtpService service;

    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OtpService(stringRedisTemplate, redisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(stringOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(objectOps);
    }

    // ─── generateAndStore ─────────────────────────────────────────────────────

    @Test
    void generateAndStore_returnsSixDigitCode() {
        String otp = service.generateAndStore(memberId, OtpType.LOGIN);

        assertThat(otp).matches("\\d{6}");
    }

    @Test
    void generateAndStore_storesLoginKeyInRedis() {
        String otp = service.generateAndStore(memberId, OtpType.LOGIN);

        then(stringOps).should().set(
                eq("member:" + memberId + ":loginToken:" + otp),
                eq(""),
                any(Duration.class));
    }

    @Test
    void generateAndStore_storesRegisterKeyInRedis() {
        String otp = service.generateAndStore(memberId, OtpType.REGISTER);

        then(stringOps).should().set(
                eq("member:" + memberId + ":registerToken:" + otp),
                eq(""),
                any(Duration.class));
    }

    // ─── verify ───────────────────────────────────────────────────────────────

    @Test
    void verify_whenKeyDeleted_returnsTrue() {
        given(stringRedisTemplate.delete(anyString())).willReturn(true);

        assertThat(service.verify(memberId, OtpType.LOGIN, "123456")).isTrue();
    }

    @Test
    void verify_whenKeyNotFound_returnsFalse() {
        given(stringRedisTemplate.delete(anyString())).willReturn(false);

        assertThat(service.verify(memberId, OtpType.LOGIN, "000000")).isFalse();
    }

    // ─── generateOtpToken ─────────────────────────────────────────────────────

    @Test
    void generateOtpToken_returnsUuidToken() {
        String token = service.generateOtpToken(memberId, OtpType.LOGIN);

        assertThat(token).isNotBlank().matches("[0-9a-f\\-]{36}");
    }

    @Test
    void generateOtpToken_storesTokenInRedis() {
        String token = service.generateOtpToken(memberId, OtpType.REGISTER);

        then(objectOps).should().set(eq("otp_token:" + token), any(), eq(5L), eq(TimeUnit.MINUTES));
    }

    // ─── resolveOtpToken ──────────────────────────────────────────────────────

    @Test
    void resolveOtpToken_validToken_returnsMemberId() {
        String token = "valid-token";
        OtpService.OtpToken otpToken = new OtpService.OtpToken();
        otpToken.setMemberId(memberId);
        otpToken.setType(OtpType.LOGIN);
        otpToken.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        given(objectOps.get("otp_token:" + token)).willReturn(otpToken);

        UUID result = service.resolveOtpToken(token, OtpType.LOGIN);

        assertThat(result).isEqualTo(memberId);
    }

    @Test
    void resolveOtpToken_missingToken_throwsIllegalArgument() {
        // mock returns null by default — no explicit stub needed
        assertThatThrownBy(() -> service.resolveOtpToken("missing-token", OtpType.LOGIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.otp.invalid");
    }

    @Test
    void resolveOtpToken_wrongType_throwsIllegalArgument() {
        String token = "type-mismatch-token";
        OtpService.OtpToken otpToken = new OtpService.OtpToken();
        otpToken.setMemberId(memberId);
        otpToken.setType(OtpType.REGISTER);
        otpToken.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        given(objectOps.get("otp_token:" + token)).willReturn(otpToken);

        assertThatThrownBy(() -> service.resolveOtpToken(token, OtpType.LOGIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.otp.expired");
    }

    @Test
    void resolveOtpToken_expiredToken_throwsIllegalArgument() {
        String token = "expired-token";
        OtpService.OtpToken otpToken = new OtpService.OtpToken();
        otpToken.setMemberId(memberId);
        otpToken.setType(OtpType.LOGIN);
        otpToken.setExpiredAt(LocalDateTime.now().minusMinutes(1));
        given(objectOps.get("otp_token:" + token)).willReturn(otpToken);

        assertThatThrownBy(() -> service.resolveOtpToken(token, OtpType.LOGIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.otp.expired");
    }

    // ─── consumeToken ─────────────────────────────────────────────────────────

    @Test
    void consumeToken_whenKeyDeleted_returnsTrue() {
        given(redisTemplate.delete("otp_token:abc")).willReturn(true);

        assertThat(service.consumeToken("abc")).isTrue();
    }

    @Test
    void consumeToken_whenKeyMissing_returnsFalse() {
        // mock returns null by default → Boolean.TRUE.equals(null) == false
        assertThat(service.consumeToken("missing")).isFalse();
    }

    // ─── recordFailedAttempt ──────────────────────────────────────────────────

    @Test
    void recordFailedAttempt_firstAttempt_returnsRemainingFour() {
        String token = "test-token";
        given(stringOps.increment("otp_attempts:" + token)).willReturn(1L);

        int remaining = service.recordFailedAttempt(token);

        assertThat(remaining).isEqualTo(4);
        then(stringRedisTemplate).should().expire(eq("otp_attempts:" + token), any(Duration.class));
    }

    @Test
    void recordFailedAttempt_thirdAttempt_returnsRemainingTwo() {
        String token = "test-token";
        given(stringOps.increment("otp_attempts:" + token)).willReturn(3L);

        assertThat(service.recordFailedAttempt(token)).isEqualTo(2);
    }

    @Test
    void recordFailedAttempt_fifthAttempt_invalidatesTokenAndReturnsZero() {
        String token = "test-token";
        given(stringOps.increment("otp_attempts:" + token)).willReturn(5L);

        int remaining = service.recordFailedAttempt(token);

        assertThat(remaining).isEqualTo(0);
        then(redisTemplate).should().delete("otp_token:" + token);
        then(stringRedisTemplate).should().delete("otp_attempts:" + token);
    }
}

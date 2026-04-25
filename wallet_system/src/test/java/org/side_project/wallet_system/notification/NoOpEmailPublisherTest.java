package org.side_project.wallet_system.notification;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatNoException;

class NoOpEmailPublisherTest {

    private final NoOpEmailPublisher publisher = new NoOpEmailPublisher();

    @Test
    void sendRegistrationOtp_doesNotThrow() {
        assertThatNoException().isThrownBy(() ->
            publisher.sendRegistrationOtp("test@example.com", "123456"));
    }

    @Test
    void sendLoginOtp_doesNotThrow() {
        assertThatNoException().isThrownBy(() ->
            publisher.sendLoginOtp("test@example.com", "654321"));
    }

    @Test
    void sendPasswordResetLink_doesNotThrow() {
        assertThatNoException().isThrownBy(() ->
            publisher.sendPasswordResetLink("test@example.com", "http://reset-link"));
    }

    @Test
    void sendDepositSuccess_doesNotThrow() {
        assertThatNoException().isThrownBy(() ->
            publisher.sendDepositSuccess("test@example.com", new BigDecimal("500.00")));
    }

    @Test
    void sendWithdrawalSuccess_doesNotThrow() {
        assertThatNoException().isThrownBy(() ->
            publisher.sendWithdrawalSuccess("test@example.com", new BigDecimal("200.00")));
    }
}

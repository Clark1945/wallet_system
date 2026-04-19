package org.side_project.wallet_system.payment;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.wallet.WalletService;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class StripePaymentServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    StripeClient stripeClient;

    @Mock WalletService walletService;
    @InjectMocks StripePaymentService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test_secret");
    }

    // ─── createPaymentIntent ──────────────────────────────────────────────────

    @Test
    void createPaymentIntent_initiatesDepositAndReturnsClientSecret() throws Exception {
        UUID memberId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000");
        UUID transactionId = UUID.randomUUID();

        given(walletService.initiateDeposit(memberId, amount)).willReturn(transactionId);

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        given(mockIntent.getClientSecret()).willReturn("pi_test_secret_key");
        given(stripeClient.paymentIntents().create(any(PaymentIntentCreateParams.class)))
                .willReturn(mockIntent);

        String secret = service.createPaymentIntent(memberId, amount);

        assertThat(secret).isEqualTo("pi_test_secret_key");
        then(walletService).should().initiateDeposit(memberId, amount);
    }

    // ─── processWebhookEvent ──────────────────────────────────────────────────

    @Test
    void processWebhookEvent_invalidSignature_returnsFalse() throws Exception {
        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(new SignatureVerificationException("bad sig", "sig-header"));

            assertThat(service.processWebhookEvent("payload", "bad-sig")).isFalse();
            then(walletService).shouldHaveNoInteractions();
        }
    }

    @Test
    void processWebhookEvent_nonSucceededEventType_returnsTrueWithoutDeposit() throws Exception {
        Event event = mock(Event.class);
        given(event.getType()).willReturn("payment_method.attached");

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenReturn(event);

            assertThat(service.processWebhookEvent("payload", "sig")).isTrue();
            then(walletService).shouldHaveNoInteractions();
        }
    }

    @Test
    void processWebhookEvent_succeededEvent_completesDepositAndReturnsTrue() throws Exception {
        UUID transactionId = UUID.randomUUID();

        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        PaymentIntent intent = mock(PaymentIntent.class);

        given(event.getType()).willReturn("payment_intent.succeeded");
        given(event.getDataObjectDeserializer()).willReturn(deserializer);
        given(deserializer.getObject()).willReturn(Optional.of(intent));
        given(intent.getId()).willReturn("pi_test_intent_001");
        given(intent.getMetadata()).willReturn(Map.of("transactionId", transactionId.toString()));

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenReturn(event);

            assertThat(service.processWebhookEvent("payload", "sig")).isTrue();
            then(walletService).should().completeDeposit(transactionId);
        }
    }

    @Test
    void processWebhookEvent_duplicateIntentId_skipsAndReturnsTrue() throws Exception {
        UUID transactionId = UUID.randomUUID();

        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        PaymentIntent intent = mock(PaymentIntent.class);

        given(event.getType()).willReturn("payment_intent.succeeded");
        given(event.getDataObjectDeserializer()).willReturn(deserializer);
        given(deserializer.getObject()).willReturn(Optional.of(intent));
        given(intent.getId()).willReturn("pi_duplicate_id");
        given(intent.getMetadata()).willReturn(Map.of("transactionId", transactionId.toString()));

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenReturn(event);

            service.processWebhookEvent("payload", "sig");    // first call — processed
            boolean result = service.processWebhookEvent("payload", "sig");  // duplicate

            assertThat(result).isTrue();
            then(walletService).should(times(1)).completeDeposit(any()); // only once
        }
    }

    @Test
    void processWebhookEvent_missingTransactionIdMetadata_returnsFalse() throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        PaymentIntent intent = mock(PaymentIntent.class);

        given(event.getType()).willReturn("payment_intent.succeeded");
        given(event.getDataObjectDeserializer()).willReturn(deserializer);
        given(deserializer.getObject()).willReturn(Optional.of(intent));
        given(intent.getId()).willReturn("pi_no_metadata");
        given(intent.getMetadata()).willReturn(Map.of());

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenReturn(event);

            assertThat(service.processWebhookEvent("payload", "sig")).isFalse();
            then(walletService).shouldHaveNoInteractions();
        }
    }
}

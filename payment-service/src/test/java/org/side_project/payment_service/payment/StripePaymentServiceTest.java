package org.side_project.payment_service.payment;

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
import org.side_project.payment_service.client.WalletServiceClient;
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

    @Mock WalletServiceClient walletServiceClient;
    @InjectMocks StripePaymentService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test_secret");
    }

    @Test
    void createPaymentIntent_initiatesDepositAndReturnsClientSecret() throws Exception {
        UUID memberId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000");
        UUID transactionId = UUID.randomUUID();

        given(walletServiceClient.initiateDeposit(memberId, amount, null)).willReturn(transactionId);

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        given(mockIntent.getId()).willReturn("pi_test_intent_id");
        given(mockIntent.getClientSecret()).willReturn("pi_test_secret_key");
        given(stripeClient.paymentIntents().create(any(PaymentIntentCreateParams.class)))
                .willReturn(mockIntent);

        String secret = service.createPaymentIntent(memberId, amount, null);

        assertThat(secret).isEqualTo("pi_test_secret_key");
        then(walletServiceClient).should().initiateDeposit(memberId, amount, null);
        then(walletServiceClient).should().linkExternalId(transactionId, "pi_test_intent_id");
    }

    @Test
    void processWebhookEvent_invalidSignature_returnsFalse() throws Exception {
        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(new SignatureVerificationException("bad sig", "sig-header"));

            assertThat(service.processWebhookEvent("payload", "bad-sig")).isFalse();
            then(walletServiceClient).shouldHaveNoInteractions();
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
            then(walletServiceClient).shouldHaveNoInteractions();
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
            then(walletServiceClient).should().completeDeposit(transactionId);
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
            then(walletServiceClient).shouldHaveNoInteractions();
        }
    }
}

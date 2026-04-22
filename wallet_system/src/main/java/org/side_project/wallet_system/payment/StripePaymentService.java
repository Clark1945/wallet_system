package org.side_project.wallet_system.payment;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.wallet.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentService {

    private final StripeClient stripeClient;
    private final WalletService walletService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    // ─────────────────────────────────────────────────────────────────────────
    // Create PaymentIntent — also creates a PENDING deposit transaction
    // ─────────────────────────────────────────────────────────────────────────

    private static final BigDecimal STRIPE_JPY_MIN = BigDecimal.valueOf(50);

    public String createPaymentIntent(UUID memberId, BigDecimal amount) throws StripeException {
        if (amount.compareTo(STRIPE_JPY_MIN) < 0) {
            throw new IllegalArgumentException("error.stripe.amount.min");
        }
        UUID transactionId = walletService.initiateDeposit(memberId, amount);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.longValue())
                .setCurrency("jpy")
                .putMetadata("memberId", memberId.toString())
                .putMetadata("transactionId", transactionId.toString())
                .setPaymentMethodOptions(
                        PaymentIntentCreateParams.PaymentMethodOptions.builder()
                                .setCard(
                                        PaymentIntentCreateParams.PaymentMethodOptions.Card.builder()
                                                .setRequestThreeDSecure(
                                                        PaymentIntentCreateParams.PaymentMethodOptions.Card
                                                                .RequestThreeDSecure.AUTOMATIC)
                                                .build())
                                .build())
                .build();

        PaymentIntent intent = stripeClient.paymentIntents().create(params);
        walletService.linkPaymentExternalId(transactionId, intent.getId());
        log.info("Stripe PaymentIntent created: id={}, memberId={}, transactionId={}, amount={}",
                 intent.getId(), memberId, transactionId, amount);
        return intent.getClientSecret();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Process webhook — on success, complete the PENDING deposit transaction
    // ─────────────────────────────────────────────────────────────────────────

    public boolean processWebhookEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return false;
        }

        if (!"payment_intent.succeeded".equals(event.getType())) {
            log.debug("Stripe webhook: ignoring event type={}", event.getType());
            return true;
        }

        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof PaymentIntent intent)) {
            log.error("Stripe webhook: expected PaymentIntent, got {}", stripeObject);
            return false;
        }

        String intentId = intent.getId();
        String transactionIdStr = intent.getMetadata().get("transactionId");
        if (transactionIdStr == null || transactionIdStr.isBlank()) {
            log.error("Stripe webhook: missing transactionId metadata for intentId={}", intentId);
            return false;
        }

        try {
            UUID transactionId = UUID.fromString(transactionIdStr);
            walletService.completeDeposit(transactionId);
            log.info("Stripe deposit completed: intentId={}, transactionId={}", intentId, transactionId);
            return true;
        } catch (RuntimeException e) {
            log.error("Stripe deposit failed: intentId={}, transactionId={}, error={}",
                      intentId, transactionIdStr, e.getMessage(), e);
            return false;
        }
    }
}

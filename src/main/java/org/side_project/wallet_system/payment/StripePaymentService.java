package org.side_project.wallet_system.payment;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.wallet.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class StripePaymentService {

    private final StripeClient stripeClient;
    private final WalletService walletService;
    private final String webhookSecret;

    /**
     * In-memory idempotency guard — prevents double-crediting if Stripe
     * retries the webhook or the browser hits /complete more than once.
     * Acceptable for test/side-project; replace with DB flag in production.
     */
    private final Set<String> processedIntents =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public StripePaymentService(
            @Value("${stripe.secret-key}") String secretKey,
            @Value("${stripe.webhook-secret}") String webhookSecret,
            WalletService walletService) {
        this.stripeClient = new StripeClient(secretKey);
        this.webhookSecret = webhookSecret;
        this.walletService = walletService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create PaymentIntent
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a Stripe PaymentIntent and returns its client_secret for
     * use by Stripe.js on the checkout page.
     * JPY has no sub-unit — Stripe expects the integer yen amount directly.
     */
    public String createPaymentIntent(UUID memberId, BigDecimal amount) throws Exception {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.longValue())
                .setCurrency("jpy")
                .putMetadata("memberId", memberId.toString())
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
        log.info("Stripe PaymentIntent created: id={}, memberId={}, amount={}",
                 intent.getId(), memberId, amount);
        return intent.getClientSecret();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Process webhook event
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies the Stripe-Signature header, then — for
     * payment_intent.succeeded events — credits the member's wallet.
     * Returns true if the event was handled successfully (caller should
     * respond 200), false if Stripe should retry (caller should respond 400).
     */
    public boolean processWebhookEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return false;
        }

        if (!"payment_intent.succeeded".equals(event.getType())) {
            // Acknowledge non-payment events without processing
            log.debug("Stripe webhook: ignoring event type={}", event.getType());
            return true;
        }

        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof PaymentIntent intent)) {
            log.error("Stripe webhook: expected PaymentIntent, got {}", stripeObject);
            return false;
        }

        String intentId = intent.getId();
        if (!processedIntents.add(intentId)) {
            log.info("Stripe webhook: duplicate event for intentId={}, skipping", intentId);
            return true;
        }

        String memberIdStr = intent.getMetadata().get("memberId");
        if (memberIdStr == null || memberIdStr.isBlank()) {
            log.error("Stripe webhook: missing memberId metadata for intentId={}", intentId);
            return false;
        }

        // JPY: Stripe amount is already in yen, no conversion needed
        BigDecimal amount = BigDecimal.valueOf(intent.getAmount());

        try {
            UUID memberId = UUID.fromString(memberIdStr);
            walletService.deposit(memberId, amount);
            log.info("Stripe deposit completed: intentId={}, memberId={}, amount={}",
                     intentId, memberId, amount);
            return true;
        } catch (Exception e) {
            log.error("Stripe deposit failed: intentId={}, error={}", intentId, e.getMessage(), e);
            processedIntents.remove(intentId); // allow retry
            return false;
        }
    }
}

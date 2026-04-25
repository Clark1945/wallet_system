package org.side_project.payment_service.payment;

import com.stripe.exception.StripeException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.payment_service.client.WalletServiceClient;
import org.side_project.payment_service.client.dto.PaymentTokenData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Controller
@RequestMapping("/payment/stripe")
@RequiredArgsConstructor
public class StripePaymentController {

    private final StripePaymentService stripePaymentService;
    private final WalletServiceClient walletServiceClient;
    private final MessageSource messageSource;

    @Value("${stripe.publishable-key}")
    private String publishableKey;

    @Value("${wallet.service.public-url}")
    private String walletServicePublicUrl;

    @GetMapping("/checkout")
    public String checkout(@RequestParam(required = false) String token,
                           Model model, Locale locale) {
        if (token == null || token.isBlank()) {
            log.warn("Stripe checkout called without token — redirecting to wallet deposit");
            return "redirect:" + walletServicePublicUrl + "/deposit";
        }

        PaymentTokenData data = walletServiceClient.validateToken(token);
        if (data == null) {
            log.warn("Stripe checkout: invalid or expired token={}", token);
            return "redirect:" + walletServicePublicUrl + "/deposit";
        }

        try {
            String clientSecret = stripePaymentService.createPaymentIntent(data.memberId(), data.amount(), data.notifyEmail());
            model.addAttribute("clientSecret", clientSecret);
            model.addAttribute("publishableKey", publishableKey);
            model.addAttribute("amount", data.amount());
            return "stripe-checkout";
        } catch (IllegalArgumentException e) {
            log.warn("Invalid deposit request: {}", e.getMessage());
            return "redirect:" + walletServicePublicUrl + "/deposit";
        } catch (StripeException e) {
            log.error("Stripe API error creating PaymentIntent: {}", e.getMessage(), e);
            return "redirect:" + walletServicePublicUrl + "/deposit";
        }
    }

    @GetMapping("/complete")
    public String complete() {
        return "redirect:" + walletServicePublicUrl + "/dashboard";
    }

    @GetMapping("/cancel")
    public String cancel() {
        return "redirect:" + walletServicePublicUrl + "/deposit";
    }

    @PostMapping("/webhook")
    @ResponseBody
    public ResponseEntity<String> webhook(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readAllBytes();
            String payload = new String(body, StandardCharsets.UTF_8);
            String sigHeader = request.getHeader("Stripe-Signature");
            boolean success = stripePaymentService.processWebhookEvent(payload, sigHeader);
            return success
                    ? ResponseEntity.ok("OK")
                    : ResponseEntity.badRequest().body("Processing failed");
        } catch (IOException e) {
            log.error("Failed to read Stripe webhook request body: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Internal error");
        }
    }
}

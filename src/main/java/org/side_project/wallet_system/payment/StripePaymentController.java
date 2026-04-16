package org.side_project.wallet_system.payment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/payment/stripe")
@RequiredArgsConstructor
public class StripePaymentController {

    private final StripePaymentService stripePaymentService;
    private final MessageSource messageSource;

    @Value("${stripe.publishable-key}")
    private String publishableKey;

    /**
     * Renders the Stripe card checkout page.
     * Creates a PaymentIntent and passes its client_secret to the template
     * so Stripe.js can confirm the payment client-side (with 3DS if needed).
     */
    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model) {
        BigDecimal amount = (BigDecimal) session.getAttribute("stripePendingAmount");
        if (amount == null) {
            log.warn("No pending Stripe amount in session — redirecting to deposit");
            return "redirect:/deposit";
        }
        UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
        try {
            String clientSecret = stripePaymentService.createPaymentIntent(memberId, amount);
            model.addAttribute("clientSecret", clientSecret);
            model.addAttribute("publishableKey", publishableKey);
            model.addAttribute("amount", amount);
            model.addAttribute("memberName", session.getAttribute("memberName"));
            return "stripe-checkout";
        } catch (Exception e) {
            log.error("Failed to create Stripe PaymentIntent: {}", e.getMessage(), e);
            return "redirect:/deposit";
        }
    }

    /**
     * Browser redirect after Stripe.js confirms successful payment.
     */
    @GetMapping("/complete")
    public String complete(HttpSession session,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        session.removeAttribute("stripePendingAmount");
        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("flash.deposit.success", null, locale));
        return "redirect:/dashboard";
    }

    /**
     * User cancelled or navigated away from the checkout page.
     */
    @GetMapping("/cancel")
    public String cancel(HttpSession session,
                         RedirectAttributes redirectAttributes,
                         Locale locale) {
        session.removeAttribute("stripePendingAmount");
        redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("flash.payment.cancelled", null, locale));
        return "redirect:/deposit";
    }

    /**
     * Webhook endpoint — called server-to-server by Stripe after payment.
     * Must be publicly accessible (no session, no CSRF).
     * Stripe retries on any non-2xx response, so return 400 on failure.
     */
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
        } catch (Exception e) {
            log.error("Failed to read Stripe webhook request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Internal error");
        }
    }
}

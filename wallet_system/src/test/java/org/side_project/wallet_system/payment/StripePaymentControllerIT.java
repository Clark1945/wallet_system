package org.side_project.wallet_system.payment;

import com.stripe.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.side_project.wallet_system.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StripePaymentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "stripe.publishable-key=pk_test_dummy_key")
class StripePaymentControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean StripePaymentService stripePaymentService;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean CustomOAuth2UserService oauth2UserService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;
    @MockitoBean LoginAttemptService loginAttemptService;

    private MockHttpSession session;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        session = new MockHttpSession();
        session.setAttribute("memberId", memberId.toString());
        session.setAttribute("memberName", "Test User");
    }

    // ── GET /payment/stripe/checkout ──────────────────────────

    @Test
    void checkout_withoutPendingAmount_redirectsToDeposit() throws Exception {
        mockMvc.perform(get("/payment/stripe/checkout")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"));
    }

    @Test
    void checkout_withPendingAmount_rendersCheckoutPage() throws Exception {
        session.setAttribute("stripePendingAmount", new BigDecimal("1000"));
        given(stripePaymentService.createPaymentIntent(any(), any())).willReturn("pi_client_secret_test");

        mockMvc.perform(get("/payment/stripe/checkout")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("stripe-checkout"))
                .andExpect(model().attribute("clientSecret", "pi_client_secret_test"))
                .andExpect(model().attribute("amount", new BigDecimal("1000")));
    }

    @Test
    void checkout_stripeException_redirectsToDeposit() throws Exception {
        session.setAttribute("stripePendingAmount", new BigDecimal("1000"));
        given(stripePaymentService.createPaymentIntent(any(), any()))
                .willThrow(new InvalidRequestException("card declined", null, null, null, 402, null));

        mockMvc.perform(get("/payment/stripe/checkout")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"));
    }

    @Test
    void checkout_illegalArgument_redirectsToDeposit() throws Exception {
        session.setAttribute("stripePendingAmount", new BigDecimal("1000"));
        given(stripePaymentService.createPaymentIntent(any(), any()))
                .willThrow(new IllegalArgumentException("invalid amount"));

        mockMvc.perform(get("/payment/stripe/checkout")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"));
    }

    // ── GET /payment/stripe/complete ──────────────────────────

    @Test
    void complete_redirectsToDashboardWithSuccess() throws Exception {
        session.setAttribute("stripePendingAmount", new BigDecimal("1000"));

        mockMvc.perform(get("/payment/stripe/complete")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void complete_clearsPendingAmountFromSession() throws Exception {
        session.setAttribute("stripePendingAmount", new BigDecimal("500"));

        mockMvc.perform(get("/payment/stripe/complete")
                        .with(user("test@example.com"))
                        .session(session));

        // session attribute should be removed
        org.assertj.core.api.Assertions.assertThat(
                session.getAttribute("stripePendingAmount")).isNull();
    }

    // ── GET /payment/stripe/cancel ────────────────────────────

    @Test
    void cancel_redirectsToDepositWithError() throws Exception {
        mockMvc.perform(get("/payment/stripe/cancel")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"))
                .andExpect(flash().attribute("error", "Payment was cancelled. Please try again."));
    }

    // ── POST /payment/stripe/webhook ──────────────────────────

    @Test
    void webhook_processSuccess_returns200WithOkBody() throws Exception {
        given(stripePaymentService.processWebhookEvent(any(), any())).willReturn(true);

        mockMvc.perform(post("/payment/stripe/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment_intent.succeeded\",\"id\":\"evt_test\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void webhook_processFails_returns400() throws Exception {
        given(stripePaymentService.processWebhookEvent(any(), any())).willReturn(false);

        mockMvc.perform(post("/payment/stripe/webhook")
                        .header("Stripe-Signature", "bad_signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment_intent.succeeded\"}"))
                .andExpect(status().isBadRequest());
    }
}

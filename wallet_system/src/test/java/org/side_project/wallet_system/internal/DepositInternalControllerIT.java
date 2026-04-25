package org.side_project.wallet_system.internal;

import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.side_project.wallet_system.config.RateLimiterService;
import org.side_project.wallet_system.config.SecurityConfig;
import org.side_project.wallet_system.internal.dto.PaymentTokenData;
import org.side_project.wallet_system.wallet.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(DepositInternalController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "internal.service.secret=test-internal-secret")
class DepositInternalControllerIT {

    private static final String SECRET = "test-internal-secret";

    @Autowired MockMvc mockMvc;
    @MockitoBean WalletService walletService;
    @MockitoBean PaymentTokenService paymentTokenService;
    @MockitoBean RateLimiterService rateLimiterService;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean CustomOAuth2UserService oauth2UserService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;
    @MockitoBean LoginAttemptService loginAttemptService;

    // ── X-Internal-Secret authentication ─────────────────────────

    @Test
    void missingSecret_returns401() throws Exception {
        mockMvc.perform(get("/internal/token/any-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSecret_returns401() throws Exception {
        mockMvc.perform(get("/internal/token/any-token")
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /internal/token/{token} ───────────────────────────────

    @Test
    void validateToken_validToken_returns200WithTokenData() throws Exception {
        UUID memberId = UUID.randomUUID();
        PaymentTokenData data = new PaymentTokenData(memberId, new BigDecimal("1000.00"), "stripe", "user@example.com");
        given(paymentTokenService.validateAndConsumeToken("abc-token")).willReturn(data);

        mockMvc.perform(get("/internal/token/abc-token")
                        .header("X-Internal-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId.toString()))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.method").value("stripe"))
                .andExpect(jsonPath("$.notifyEmail").value("user@example.com"));
    }

    @Test
    void validateToken_invalidOrExpiredToken_returns404() throws Exception {
        given(paymentTokenService.validateAndConsumeToken("expired-token")).willReturn(null);

        mockMvc.perform(get("/internal/token/expired-token")
                        .header("X-Internal-Secret", SECRET))
                .andExpect(status().isNotFound());
    }

    // ── POST /internal/deposit/initiate ──────────────────────────

    @Test
    void initiateDeposit_validRequest_returns200WithTransactionId() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        given(walletService.initiateDeposit(eq(memberId), eq(new BigDecimal("500.00")), eq("user@example.com")))
                .willReturn(transactionId);

        String body = String.format(
                "{\"memberId\":\"%s\",\"amount\":500.00,\"notifyEmail\":\"user@example.com\"}", memberId);

        mockMvc.perform(post("/internal/deposit/initiate")
                        .header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()));
    }

    @Test
    void initiateDeposit_nullNotifyEmail_delegatesToService() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        given(walletService.initiateDeposit(eq(memberId), any(), eq(null))).willReturn(transactionId);

        String body = String.format("{\"memberId\":\"%s\",\"amount\":200.00}", memberId);

        mockMvc.perform(post("/internal/deposit/initiate")
                        .header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        then(walletService).should().initiateDeposit(eq(memberId), any(), eq(null));
    }

    // ── POST /internal/deposit/link-external ─────────────────────

    @Test
    void linkExternalId_validRequest_returns200AndDelegates() throws Exception {
        UUID transactionId = UUID.randomUUID();
        String body = String.format(
                "{\"transactionId\":\"%s\",\"externalId\":\"pi_test_123\"}", transactionId);

        mockMvc.perform(post("/internal/deposit/link-external")
                        .header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        then(walletService).should().linkPaymentExternalId(transactionId, "pi_test_123");
    }

    // ── POST /internal/deposit/complete ──────────────────────────

    @Test
    void completeDeposit_validRequest_returns200AndDelegates() throws Exception {
        UUID transactionId = UUID.randomUUID();
        String body = String.format("{\"transactionId\":\"%s\"}", transactionId);

        mockMvc.perform(post("/internal/deposit/complete")
                        .header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        then(walletService).should().completeDeposit(transactionId);
    }

    // ── POST /internal/deposit/complete-by-external ──────────────

    @Test
    void completeByExternal_found_returns200() throws Exception {
        given(walletService.completeDepositByExternalId("order-001")).willReturn(true);

        mockMvc.perform(post("/internal/deposit/complete-by-external")
                        .header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"order-001\"}"))
                .andExpect(status().isOk());

        then(walletService).should().completeDepositByExternalId("order-001");
    }

    @Test
    void completeByExternal_notFound_returns404() throws Exception {
        given(walletService.completeDepositByExternalId("unknown-order")).willReturn(false);

        mockMvc.perform(post("/internal/deposit/complete-by-external")
                        .header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"unknown-order\"}"))
                .andExpect(status().isNotFound());

        then(walletService).should(never()).completeDeposit(any());
    }
}

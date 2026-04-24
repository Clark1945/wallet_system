package org.side_project.wallet_system.wallet;

import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.side_project.wallet_system.config.RateLimiterService;
import org.side_project.wallet_system.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(WithdrawWebhookController.class)
@Import(SecurityConfig.class)
class WithdrawWebhookControllerIT {

    private static final String TEST_SECRET = "test-webhook-secret";

    @Autowired MockMvc mockMvc;
    @MockitoBean WalletService walletService;
    @MockitoBean RateLimiterService rateLimiterService;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean CustomOAuth2UserService oauth2UserService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;
    @MockitoBean LoginAttemptService loginAttemptService;

    private String json(String transactionId, String result) {
        return String.format("{\"transactionId\":\"%s\",\"result\":\"%s\"}", transactionId, result);
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    // ── HMAC verification ─────────────────────────────────────────

    @Test
    void missingSignatureHeader_returns401() throws Exception {
        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(UUID.randomUUID().toString(), "SUCCESS")))
                .andExpect(status().isUnauthorized());

        verify(walletService, never()).completeWithdrawal(any());
        verify(walletService, never()).failWithdrawal(any());
    }

    @Test
    void wrongSignature_returns401() throws Exception {
        String body = json(UUID.randomUUID().toString(), "SUCCESS");

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "sha256=deadbeef")
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(walletService, never()).completeWithdrawal(any());
    }

    // ── SUCCESS path ──────────────────────────────────────────────

    @Test
    void success_callsCompleteWithdrawal_returns200() throws Exception {
        String txId = UUID.randomUUID().toString();
        String body = json(txId, "SUCCESS");

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        verify(walletService).completeWithdrawal(UUID.fromString(txId));
        verify(walletService, never()).failWithdrawal(any());
    }

    @Test
    void success_caseInsensitive_callsCompleteWithdrawal() throws Exception {
        String txId = UUID.randomUUID().toString();
        String body = json(txId, "success");

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        verify(walletService).completeWithdrawal(UUID.fromString(txId));
    }

    // ── FAILURE path ──────────────────────────────────────────────

    @Test
    void failure_callsFailWithdrawal_returns200() throws Exception {
        String txId = UUID.randomUUID().toString();
        String body = json(txId, "FAILED");

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        verify(walletService).failWithdrawal(UUID.fromString(txId));
        verify(walletService, never()).completeWithdrawal(any());
    }

    @Test
    void nullResult_callsFailWithdrawal_returns200() throws Exception {
        String txId = UUID.randomUUID().toString();
        String body = String.format("{\"transactionId\":\"%s\"}", txId);

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        verify(walletService).failWithdrawal(UUID.fromString(txId));
    }

    // ── Validation errors ─────────────────────────────────────────

    @Test
    void missingTransactionId_returns400() throws Exception {
        String body = "{\"result\":\"SUCCESS\"}";

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(body))
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(walletService, never()).completeWithdrawal(any());
        verify(walletService, never()).failWithdrawal(any());
    }

    @Test
    void blankTransactionId_returns400() throws Exception {
        String body = "{\"transactionId\":\"\",\"result\":\"SUCCESS\"}";

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(body))
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidUuidTransactionId_returns400() throws Exception {
        String body = json("not-a-uuid", "SUCCESS");

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(body))
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── Service exceptions → 500 ──────────────────────────────────

    @Test
    void completeWithdrawal_throws_returns500() throws Exception {
        String txId = UUID.randomUUID().toString();
        String body = json(txId, "SUCCESS");
        doThrow(new RuntimeException("db error")).when(walletService).completeWithdrawal(any());

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(body))
                        .content(body))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void failWithdrawal_throws_returns500() throws Exception {
        String txId = UUID.randomUUID().toString();
        String body = json(txId, "FAILED");
        doThrow(new RuntimeException("db error")).when(walletService).failWithdrawal(any());

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sign(body))
                        .content(body))
                .andExpect(status().isInternalServerError());
    }
}

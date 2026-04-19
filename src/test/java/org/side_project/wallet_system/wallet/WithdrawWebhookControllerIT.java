package org.side_project.wallet_system.wallet;

import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WithdrawWebhookController.class)
@Import(SecurityConfig.class)
class WithdrawWebhookControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean WalletService walletService;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean CustomOAuth2UserService oauth2UserService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;

    private String json(String transactionId, String result) {
        return String.format("{\"transactionId\":\"%s\",\"result\":\"%s\"}", transactionId, result);
    }

    // ── SUCCESS path ──────────────────────────────────────────────

    @Test
    void success_callsCompleteWithdrawal_returns200() throws Exception {
        String txId = UUID.randomUUID().toString();

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(txId, "SUCCESS")))
                .andExpect(status().isOk());

        verify(walletService).completeWithdrawal(UUID.fromString(txId));
        verify(walletService, never()).failWithdrawal(any());
    }

    @Test
    void success_caseInsensitive_callsCompleteWithdrawal() throws Exception {
        String txId = UUID.randomUUID().toString();

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(txId, "success")))
                .andExpect(status().isOk());

        verify(walletService).completeWithdrawal(UUID.fromString(txId));
    }

    // ── FAILURE path ──────────────────────────────────────────────

    @Test
    void failure_callsFailWithdrawal_returns200() throws Exception {
        String txId = UUID.randomUUID().toString();

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(txId, "FAILED")))
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
                        .content(body))
                .andExpect(status().isOk());

        verify(walletService).failWithdrawal(UUID.fromString(txId));
    }

    // ── Validation errors ─────────────────────────────────────────

    @Test
    void missingTransactionId_returns400() throws Exception {
        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"SUCCESS\"}"))
                .andExpect(status().isBadRequest());

        verify(walletService, never()).completeWithdrawal(any());
        verify(walletService, never()).failWithdrawal(any());
    }

    @Test
    void blankTransactionId_returns400() throws Exception {
        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"\",\"result\":\"SUCCESS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidUuidTransactionId_returns400() throws Exception {
        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("not-a-uuid", "SUCCESS")))
                .andExpect(status().isBadRequest());
    }

    // ── Service exceptions → 500 ──────────────────────────────────

    @Test
    void completeWithdrawal_throws_returns500() throws Exception {
        String txId = UUID.randomUUID().toString();
        doThrow(new RuntimeException("db error")).when(walletService).completeWithdrawal(any());

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(txId, "SUCCESS")))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void failWithdrawal_throws_returns500() throws Exception {
        String txId = UUID.randomUUID().toString();
        doThrow(new RuntimeException("db error")).when(walletService).failWithdrawal(any());

        mockMvc.perform(post("/withdraw/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(txId, "FAILED")))
                .andExpect(status().isInternalServerError());
    }
}

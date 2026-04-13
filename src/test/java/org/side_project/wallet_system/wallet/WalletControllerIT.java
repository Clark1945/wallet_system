package org.side_project.wallet_system.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.payment.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
class WalletControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean WalletService walletService;

    private MockHttpSession session;
    private UUID memberId;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();

        wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("500.00"));
        wallet.setWalletCode("TestCode0001");

        session = new MockHttpSession();
        session.setAttribute("memberId", memberId.toString());
        session.setAttribute("memberName", "Test User");

        given(walletService.getWallet(memberId)).willReturn(wallet);
        given(walletService.getTransactions(memberId)).willReturn(List.of());
    }

    // ── dashboard ─────────────────────────────────────────────

    @Test
    void dashboard_withoutSession_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void dashboard_withSession_returnsOkAndDashboardView() throws Exception {
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("wallet", wallet))
                .andExpect(model().attribute("transactions", List.of()));
    }

    // ── deposit ───────────────────────────────────────────────

    @Test
    void deposit_validAmount_redirectsToDashboardWithSuccess() throws Exception {
        mockMvc.perform(post("/deposit")
                        .param("amount", "200.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("success", "存款成功"));

        then(walletService).should().deposit(eq(memberId), eq(new BigDecimal("200.00")));
    }

    @Test
    void deposit_serviceThrows_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("金額必須大於 0"))
                .given(walletService).deposit(any(), any());

        mockMvc.perform(post("/deposit")
                        .param("amount", "0")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", "金額必須大於 0"));
    }

    // ── withdraw ──────────────────────────────────────────────

    @Test
    void withdraw_validAmount_redirectsToDashboardWithSuccess() throws Exception {
        mockMvc.perform(post("/withdraw")
                        .param("amount", "100.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("success", "提款成功"));

        then(walletService).should().withdraw(eq(memberId), eq(new BigDecimal("100.00")));
    }

    @Test
    void withdraw_insufficientBalance_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("餘額不足"))
                .given(walletService).withdraw(any(), any());

        mockMvc.perform(post("/withdraw")
                        .param("amount", "99999.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", "餘額不足"));
    }

    // ── transfer ──────────────────────────────────────────────

    @Test
    void transfer_validRequest_redirectsToDashboardWithSuccess() throws Exception {
        mockMvc.perform(post("/transfer")
                        .param("toWalletCode", "OtherCode0001")
                        .param("amount", "50.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("success", "轉帳成功"));

        then(walletService).should()
                .transfer(eq(memberId), eq("OtherCode0001"), eq(new BigDecimal("50.00")));
    }

    @Test
    void transfer_invalidCode_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("找不到此錢包代碼"))
                .given(walletService).transfer(any(), any(), any());

        mockMvc.perform(post("/transfer")
                        .param("toWalletCode", "NotExist0001")
                        .param("amount", "50.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", "找不到此錢包代碼"));
    }

    @Test
    void transfer_selfTransfer_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("無法轉帳給自己"))
                .given(walletService).transfer(any(), any(), any());

        mockMvc.perform(post("/transfer")
                        .param("toWalletCode", "TestCode0001")
                        .param("amount", "50.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", "無法轉帳給自己"));
    }
}

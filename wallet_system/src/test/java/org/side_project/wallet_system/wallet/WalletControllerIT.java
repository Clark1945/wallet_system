package org.side_project.wallet_system.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.side_project.wallet_system.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
@Import(SecurityConfig.class)
class WalletControllerIT {

    private static Locale originalLocale;

    @BeforeAll
    static void setTestLocale() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean WalletService walletService;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean CustomOAuth2UserService oauth2UserService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;
    @MockitoBean LoginAttemptService loginAttemptService;

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
        given(walletService.getTransactions(eq(memberId), any(), any(), any(), eq(0), eq(10)))
            .willReturn(new PageImpl<>(List.of()));
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
        mockMvc.perform(get("/dashboard")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("wallet", wallet))
                .andExpect(model().attributeExists("txPage"));
    }

    // ── GET /deposit ──────────────────────────────────────────

    @Test
    void depositPage_withoutSession_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/deposit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void depositPage_withSession_returnsOkAndDepositView() throws Exception {
        mockMvc.perform(get("/deposit")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("deposit"))
                .andExpect(model().attribute("wallet", wallet));
    }

    // ── POST /deposit ─────────────────────────────────────────

    @Test
    void deposit_stripeMethod_redirectsToStripeCheckout() throws Exception {
        mockMvc.perform(post("/deposit").with(csrf()).with(user("test@example.com"))
                        .param("amount", "200.00")
                        .param("paymentMethod", "stripe")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payment/stripe/checkout"));

        then(walletService).should(never()).deposit(any(), any());
    }

    @Test
    void deposit_sbpaymentMethod_redirectsToSBPaymentRequest() throws Exception {
        mockMvc.perform(post("/deposit").with(csrf()).with(user("test@example.com"))
                        .param("amount", "500.00")
                        .param("paymentMethod", "sbpayment")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payment/sbpayment/request"));

        then(walletService).should(never()).deposit(any(), any());
    }

    @Test
    void deposit_sbpaymentWithNegativeAmount_redirectsToDepositWithError() throws Exception {
        mockMvc.perform(post("/deposit").with(csrf()).with(user("test@example.com"))
                        .param("amount", "0")
                        .param("paymentMethod", "sbpayment")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"))
                .andExpect(flash().attribute("error", "Amount must be greater than 0"));

        then(walletService).should(never()).deposit(any(), any());
    }

    @Test
    void deposit_serviceThrows_redirectsToDepositWithError() throws Exception {
        willThrow(new IllegalArgumentException("error.amount.positive"))
                .given(walletService).deposit(any(), any());

        mockMvc.perform(post("/deposit").with(csrf()).with(user("test@example.com"))
                        .param("amount", "0")
                        .param("paymentMethod", "stripe")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"))
                .andExpect(flash().attribute("error", "Amount must be greater than 0"));
    }

    @Test
    void deposit_unknownPaymentMethod_redirectsToDepositWithError() throws Exception {
        mockMvc.perform(post("/deposit").with(csrf()).with(user("test@example.com"))
                        .param("amount", "100.00")
                        .param("paymentMethod", "paypal")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"))
                .andExpect(flash().attribute("error", "Unknown payment method"));

        then(walletService).should(never()).deposit(any(), any());
    }

    // ── GET /withdraw ─────────────────────────────────────────

    @Test
    void withdrawPage_withoutSession_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/withdraw"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void withdrawPage_withSession_returnsWithdrawView() throws Exception {
        mockMvc.perform(get("/withdraw")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("withdraw"))
                .andExpect(model().attribute("wallet", wallet));
    }

    // ── withdraw ──────────────────────────────────────────────

    @Test
    void withdraw_validAmount_redirectsToDashboardWithPending() throws Exception {
        mockMvc.perform(post("/withdraw").with(csrf()).with(user("test@example.com"))
                        .param("amount", "100.00")
                        .param("bankCode", "012")
                        .param("bankAccount", "1234567890")
                        .param("notifyEmail", "test@example.com")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("success",
                        "Withdrawal submitted. Funds will be transferred within a few seconds."));

        then(walletService).should().initiateWithdrawal(
                eq(memberId), eq(new BigDecimal("100.00")), eq("012"), eq("1234567890"));
    }

    @Test
    void withdraw_insufficientBalance_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("error.insufficient.balance"))
                .given(walletService).initiateWithdrawal(any(), any(), any(), any());

        mockMvc.perform(post("/withdraw").with(csrf()).with(user("test@example.com"))
                        .param("amount", "99999.00")
                        .param("bankCode", "012")
                        .param("bankAccount", "1234567890")
                        .param("notifyEmail", "test@example.com")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", "Insufficient balance"));
    }

    @Test
    void withdraw_noMemberIdInSession_redirectsToLogin() throws Exception {
        MockHttpSession noIdSession = new MockHttpSession();
        noIdSession.setAttribute("memberName", "Test User");

        mockMvc.perform(post("/withdraw").with(csrf()).with(user("test@example.com"))
                        .param("amount", "100.00")
                        .param("bankCode", "012")
                        .param("bankAccount", "1234567890")
                        .param("notifyEmail", "test@example.com")
                        .session(noIdSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        then(walletService).should(never()).initiateWithdrawal(any(), any(), any(), any());
    }

    // ── GET /transfer ─────────────────────────────────────────

    @Test
    void transferPage_withoutSession_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/transfer"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void transferPage_withSession_returnsOkAndTransferView() throws Exception {
        mockMvc.perform(get("/transfer")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("transfer"))
                .andExpect(model().attribute("wallet", wallet));
    }

    // ── POST /transfer ────────────────────────────────────────

    @Test
    void transfer_noMemberIdInSession_redirectsToLogin() throws Exception {
        MockHttpSession noIdSession = new MockHttpSession();
        noIdSession.setAttribute("memberName", "Test User");

        mockMvc.perform(post("/transfer").with(csrf()).with(user("test@example.com"))
                        .param("toWalletCode", "OtherCode0001")
                        .param("amount", "50.00")
                        .session(noIdSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        then(walletService).should(never()).transfer(any(), any(), any());
    }

    @Test
    void transfer_validRequest_redirectsToDashboardWithSuccess() throws Exception {
        mockMvc.perform(post("/transfer").with(csrf()).with(user("test@example.com"))
                        .param("toWalletCode", "OtherCode0001")
                        .param("amount", "50.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("success", "Transfer successful"));

        then(walletService).should()
                .transfer(eq(memberId), eq("OtherCode0001"), eq(new BigDecimal("50.00")));
    }

    @Test
    void transfer_invalidCode_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("error.wallet.not.found"))
                .given(walletService).transfer(any(), any(), any());

        mockMvc.perform(post("/transfer").with(csrf()).with(user("test@example.com"))
                        .param("toWalletCode", "NotExist0001")
                        .param("amount", "50.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("error", "Wallet code not found"));
    }

    @Test
    void transfer_selfTransfer_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("error.self.transfer"))
                .given(walletService).transfer(any(), any(), any());

        mockMvc.perform(post("/transfer").with(csrf()).with(user("test@example.com"))
                        .param("toWalletCode", "TestCode0001")
                        .param("amount", "50.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("error", "Cannot transfer to yourself"));
    }

    @Test
    void transfer_insufficientBalance_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("error.insufficient.balance"))
                .given(walletService).transfer(any(), any(), any());

        mockMvc.perform(post("/transfer").with(csrf()).with(user("test@example.com"))
                        .param("toWalletCode", "OtherCode0001")
                        .param("amount", "9999.00")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("error", "Insufficient balance"));
    }

    // ── dashboard filter params ───────────────────────────────

    @Test
    void dashboard_withTypeFilter_setsFilterAttributesOnModel() throws Exception {
        given(walletService.getTransactions(eq(memberId), eq(org.side_project.wallet_system.transaction.TransactionType.DEPOSIT), any(), any(), eq(0), eq(10)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/dashboard")
                        .with(user("test@example.com"))
                        .param("type", "DEPOSIT")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("filterType", "DEPOSIT"));
    }

    @Test
    void dashboard_withDateFilter_setsFilterAttributesOnModel() throws Exception {
        given(walletService.getTransactions(eq(memberId), any(), any(), any(), eq(0), eq(10)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/dashboard")
                        .with(user("test@example.com"))
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-12-31")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("filterStart", java.time.LocalDate.of(2024, 1, 1)))
                .andExpect(model().attribute("filterEnd", java.time.LocalDate.of(2024, 12, 31)));
    }
}

package org.side_project.wallet_system.payment;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SBPaymentController.class)
@Import(SecurityConfig.class)
class SBPaymentControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean SBPaymentService sbPaymentService;
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

    // ── GET /payment/sbpayment/request ────────────────────────

    @Test
    void request_withoutPendingAmount_redirectsToDeposit() throws Exception {
        mockMvc.perform(get("/payment/sbpayment/request")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"));
    }

    @Test
    void request_withPendingAmount_rendersSBPaymentForm() throws Exception {
        session.setAttribute("sbpaymentPendingAmount", new BigDecimal("1000"));
        SBPaymentRequest req = SBPaymentRequest.builder()
                .gatewayUrl("https://stbfep.sps-system.com/Extra/BuyRequestAction.do")
                .payMethod("credit").merchantId("19788").serviceId("001")
                .custCode(memberId.toString()).spsCustNo("").spsPaymentNo("")
                .orderId("testorder00000001").itemId("WALLET_DEPOSIT").payItemId("")
                .itemName("Wallet Deposit").tax("").amount("1000")
                .payType("0").autoChargeType("").serviceType("0")
                .divSettele("").lastChargeMonth("").campType("").trackingId("")
                .terminalType("0")
                .successUrl("http://localhost:8080/payment/sbpayment/complete")
                .cancelUrl("http://localhost:8080/payment/sbpayment/cancel")
                .errorUrl("http://localhost:8080/payment/sbpayment/error")
                .pageconUrl("http://localhost:8080/payment/sbpayment/result")
                .free1("").free2("").free3("").freeCsv("")
                .requestDate("20240101120000").limitSecond("600")
                .spsHashcode("abc123").build();
        given(sbPaymentService.buildRequest(memberId, new BigDecimal("1000"))).willReturn(req);

        mockMvc.perform(get("/payment/sbpayment/request")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("sb-payment"))
                .andExpect(model().attributeExists("req"));
    }

    // ── POST /payment/sbpayment/result (public CGI) ───────────

    @Test
    void result_successFromSBPS_returnsOkBody() throws Exception {
        given(sbPaymentService.processResult(anyMap())).willReturn(true);

        mockMvc.perform(post("/payment/sbpayment/result")
                        // No csrf() and no user() — this is a public server-to-server endpoint
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("res_result=OK&order_id=testorder00000001"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK,"));
    }

    @Test
    void result_failureFromSBPS_returnsNgBody() throws Exception {
        given(sbPaymentService.processResult(anyMap())).willReturn(false);

        mockMvc.perform(post("/payment/sbpayment/result")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("res_result=NG&order_id=testorder00000001"))
                .andExpect(status().isOk())
                .andExpect(content().string("NG,Payment processing failed"));
    }

    // ── GET /payment/sbpayment/complete ───────────────────────

    @Test
    void complete_redirectsToDashboardWithSuccess() throws Exception {
        session.setAttribute("sbpaymentPendingAmount", new BigDecimal("1000"));

        mockMvc.perform(get("/payment/sbpayment/complete")
                        .with(csrf()).with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("success", "Deposit successful"));
    }

    // ── GET /payment/sbpayment/cancel ─────────────────────────

    @Test
    void cancel_redirectsToDepositWithError() throws Exception {
        mockMvc.perform(get("/payment/sbpayment/cancel")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"))
                .andExpect(flash().attribute("error", "Payment was cancelled. Please try again."));
    }

    // ── GET /payment/sbpayment/error ──────────────────────────

    @Test
    void error_redirectsToDepositWithError() throws Exception {
        mockMvc.perform(get("/payment/sbpayment/error")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deposit"))
                .andExpect(flash().attribute("error",
                        "Payment failed. Please try again or choose a different method."));
    }
}

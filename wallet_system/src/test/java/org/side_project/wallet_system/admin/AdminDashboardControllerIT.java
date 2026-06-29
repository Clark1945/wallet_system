package org.side_project.wallet_system.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.admin.dto.AdminStats;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.side_project.wallet_system.audit.AuditService;
import org.side_project.wallet_system.config.RateLimiterService;
import org.side_project.wallet_system.config.SecurityConfig;
import org.side_project.wallet_system.config.SessionConstants;
import org.side_project.wallet_system.transaction.TransactionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDashboardController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "payment.service.base-url=http://localhost:8082",
    "internal.service.secret=test-internal-secret"
})
class AdminDashboardControllerIT {

    @Autowired MockMvc mockMvc;

    @MockitoBean AdminService adminService;
    @MockitoBean RateLimiterService rateLimiterService;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean CustomOAuth2UserService oauth2UserService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;
    @MockitoBean LoginAttemptService loginAttemptService;
    @MockitoBean AuditService auditService;

    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() {
        adminSession = new MockHttpSession();
        adminSession.setAttribute(SessionConstants.MEMBER_ID, java.util.UUID.randomUUID().toString());
        adminSession.setAttribute(SessionConstants.MEMBER_NAME, "Admin User");
        adminSession.setAttribute(SessionConstants.IS_ADMIN, Boolean.TRUE);

        Map<TransactionStatus, Long> counts = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus s : TransactionStatus.values()) counts.put(s, 0L);
        given(adminService.getStats()).willReturn(new AdminStats(
                0L, counts, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        given(adminService.queryTransactions(any(), any(), any(), any(), eq(0), eq(10)))
                .willReturn(new PageImpl<>(List.of()));
    }

    @Test
    void admin_withAdminSession_returnsOkAndAdminView() throws Exception {
        mockMvc.perform(get("/admin").with(user("admin@example.com")).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dashboard"))
                .andExpect(model().attributeExists("stats"))
                .andExpect(model().attributeExists("txPage"));
    }

    @Test
    void admin_withFilters_setsFilterAttributesOnModel() throws Exception {
        given(adminService.queryTransactions(eq(TransactionStatus.FAILED), eq("ord-1"), any(), any(), eq(0), eq(10)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/admin")
                        .with(user("admin@example.com"))
                        .param("status", "FAILED")
                        .param("orderNo", "ord-1")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterStatus", "FAILED"))
                .andExpect(model().attribute("filterOrderNo", "ord-1"));
    }

    @Test
    void admin_withNonAdminSession_redirectsToDashboard() throws Exception {
        MockHttpSession nonAdmin = new MockHttpSession();
        nonAdmin.setAttribute(SessionConstants.MEMBER_ID, java.util.UUID.randomUUID().toString());
        nonAdmin.setAttribute(SessionConstants.IS_ADMIN, Boolean.FALSE);

        mockMvc.perform(get("/admin").with(user("user@example.com")).session(nonAdmin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void admin_withoutAuthentication_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}

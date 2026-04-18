package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.side_project.wallet_system.config.SecurityConfig;
import org.side_project.wallet_system.config.SessionConstants;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean OtpService otpService;
    @MockitoBean EmailService emailService;
    @MockitoBean PasswordResetService passwordResetService;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean CustomOAuth2UserService oauth2UserService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;

    // ─── Login page ──────────────────────────────────────────────────────────────

    @Test
    void loginPage_returnsOk() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void loginPage_withErrorParam_showsErrorMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("error"));
    }

    // ─── Register page ───────────────────────────────────────────────────────────

    @Test
    void registerPage_returnsOk() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void register_success_redirectsToRegisterOtp() throws Exception {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        given(authService.initiateRegistration(any(), anyInt(), any(), any())).willReturn(member);
        given(otpService.generateAndStore(any(), eq(OtpType.REGISTER))).willReturn("123456");

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "Bob")
                        .param("age", "28")
                        .param("email", "bob@test.com")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register/otp"))
                .andExpect(flash().attributeExists("info"));
    }

    @Test
    void register_duplicateEmail_redirectsBackWithError() throws Exception {
        given(authService.initiateRegistration(any(), anyInt(), any(), any()))
                .willThrow(new IllegalArgumentException("error.email.duplicate"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "Bob")
                        .param("age", "28")
                        .param("email", "dup@test.com")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attributeExists("error"));
    }

    // ─── Register OTP ────────────────────────────────────────────────────────────

    @Test
    void registrationOtpPage_withSession_returnsOtpView() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConstants.OTP_EMAIL, "bob@test.com");
        session.setAttribute(SessionConstants.OTP_MEMBER_ID, UUID.randomUUID().toString());

        mockMvc.perform(get("/register/otp").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("otp-verify"));
    }

    @Test
    void registrationOtpPage_withoutSession_redirectsToRegister() throws Exception {
        mockMvc.perform(get("/register/otp"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"));
    }

    @Test
    void verifyRegistrationOtp_valid_redirectsToLogin() throws Exception {
        UUID memberId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConstants.OTP_MEMBER_ID, memberId.toString());
        given(otpService.verify(eq(memberId), eq(OtpType.REGISTER), eq("123456"))).willReturn(true);

        mockMvc.perform(post("/register/otp").with(csrf()).session(session)
                        .param("code", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void verifyRegistrationOtp_invalid_redirectsBackWithError() throws Exception {
        UUID memberId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConstants.OTP_MEMBER_ID, memberId.toString());
        given(otpService.verify(any(), eq(OtpType.REGISTER), any())).willReturn(false);

        mockMvc.perform(post("/register/otp").with(csrf()).session(session)
                        .param("code", "000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register/otp"))
                .andExpect(flash().attributeExists("error"));
    }

    // ─── Login OTP ───────────────────────────────────────────────────────────────

    @Test
    void loginOtpPage_withPendingOtp_returnsOtpView() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConstants.PENDING_OTP, true);
        session.setAttribute(SessionConstants.OTP_EMAIL, "user@test.com");

        mockMvc.perform(get("/login/otp").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("otp-verify"));
    }

    @Test
    void loginOtpPage_withoutPendingOtp_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/login/otp"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void verifyLoginOtp_valid_redirectsToDashboard() throws Exception {
        UUID memberId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConstants.PENDING_OTP, true);
        session.setAttribute(SessionConstants.MEMBER_ID, memberId.toString());
        session.setAttribute(SessionConstants.OTP_EMAIL, "user@test.com");
        given(otpService.verify(eq(memberId), eq(OtpType.LOGIN), eq("654321"))).willReturn(true);

        mockMvc.perform(post("/login/otp").with(csrf()).session(session)
                        .param("code", "654321"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    // ─── Forgot Password ─────────────────────────────────────────────────────────

    @Test
    void forgotPasswordPage_returnsOk() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"));
    }

    @Test
    void sendPasswordReset_always_showsGenericConfirm() throws Exception {
        given(authService.findByEmail(any())).willReturn(Optional.empty());

        mockMvc.perform(post("/forgot-password").with(csrf())
                        .param("email", "nobody@test.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attributeExists("info"));
    }

    // ─── Reset Password ──────────────────────────────────────────────────────────

    @Test
    void resetPasswordPage_invalidToken_redirectsToLogin() throws Exception {
        given(passwordResetService.isValid(any(), any())).willReturn(false);

        mockMvc.perform(get("/reset-password")
                        .param("mid", UUID.randomUUID().toString())
                        .param("token", "invalid-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void resetPasswordPage_validToken_returnsResetView() throws Exception {
        given(passwordResetService.isValid(any(), any())).willReturn(true);

        mockMvc.perform(get("/reset-password")
                        .param("mid", UUID.randomUUID().toString())
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"));
    }

    @Test
    void resetPassword_validToken_redirectsToLoginWithSuccess() throws Exception {
        given(passwordResetService.verify(any(), any())).willReturn(true);

        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("mid", UUID.randomUUID().toString())
                        .param("token", "valid-token")
                        .param("password", "newpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("success"));
    }
}

package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.auth.controller.AuthController;
import org.side_project.wallet_system.auth.controller.AuthPageController;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.objects.OtpType;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.AuthFlowService;
import org.side_project.wallet_system.auth.service.AuthService;
import org.side_project.wallet_system.auth.service.OtpService;
import org.side_project.wallet_system.auth.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.side_project.wallet_system.config.SecurityConfig;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, AuthPageController.class})
@Import({SecurityConfig.class, AuthFlowService.class})
class AuthControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean
    AuthService authService;
    @MockitoBean
    PasswordResetService passwordResetService;
    @MockitoBean
    OtpService otpService;
    @MockitoBean
    MemberRepository memberRepository;
    @MockitoBean
    CustomOAuth2UserService oauth2UserService;
    @MockitoBean
    LoginSuccessHandler loginSuccessHandler;

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
        given(otpService.generateOtpToken(any(), any())).willReturn("test-token-123");

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "Bob")
                        .param("age", "28")
                        .param("email", "bob@test.com")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register/otp?token=test-token-123"))
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
    void registrationOtpPage_withToken_returnsOtpView() throws Exception {
        UUID memberId = UUID.randomUUID();
        given(otpService.resolveOtpToken("valid-token", OtpType.REGISTER)).willReturn(memberId);
        given(authService.getEmailById(memberId)).willReturn("bob@test.com");

        mockMvc.perform(get("/register/otp").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("otp-verify"));
    }

    @Test
    void registrationOtpPage_withoutToken_redirectsToRegister() throws Exception {
        mockMvc.perform(get("/register/otp"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"));
    }

    @Test
    void verifyRegistrationOtp_valid_redirectsToLogin() throws Exception {
        UUID memberId = UUID.randomUUID();
        given(otpService.resolveOtpToken("valid-token", OtpType.REGISTER)).willReturn(memberId);
        given(otpService.consumeToken("valid-token")).willReturn(true);
        // verifyAndActivate is void — default mock does nothing (success path)

        mockMvc.perform(post("/register/otp").with(csrf())
                        .param("token", "valid-token")
                        .param("code", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void verifyRegistrationOtp_invalid_redirectsBackWithError() throws Exception {
        UUID memberId = UUID.randomUUID();
        given(otpService.resolveOtpToken("valid-token", OtpType.REGISTER)).willReturn(memberId);
        given(otpService.recordFailedAttempt("valid-token")).willReturn(4); // 4 remaining
        willThrow(new IllegalArgumentException("error.otp.invalid"))
                .given(authService).verifyAndActivate(any(), any());

        mockMvc.perform(post("/register/otp").with(csrf())
                        .param("token", "valid-token")
                        .param("code", "000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register/otp?token=valid-token"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void verifyRegistrationOtp_locked_redirectsToRegister() throws Exception {
        UUID memberId = UUID.randomUUID();
        given(otpService.resolveOtpToken("valid-token", OtpType.REGISTER)).willReturn(memberId);
        given(otpService.recordFailedAttempt("valid-token")).willReturn(0); // locked
        willThrow(new IllegalArgumentException("error.otp.invalid"))
                .given(authService).verifyAndActivate(any(), any());

        mockMvc.perform(post("/register/otp").with(csrf())
                        .param("token", "valid-token")
                        .param("code", "000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attributeExists("error"));
    }

    // ─── Login OTP ───────────────────────────────────────────────────────────────

    @Test
    void loginOtpPage_withToken_returnsOtpView() throws Exception {
        UUID memberId = UUID.randomUUID();
        given(otpService.resolveOtpToken("login-token", OtpType.LOGIN)).willReturn(memberId);
        given(authService.getEmailById(memberId)).willReturn("user@test.com");

        mockMvc.perform(get("/login/otp").param("token", "login-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("otp-verify"));
    }

    @Test
    void loginOtpPage_withoutToken_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/login/otp"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void verifyLoginOtp_valid_redirectsToDashboard() throws Exception {
        UUID memberId = UUID.randomUUID();
        given(otpService.resolveOtpToken("login-token", OtpType.LOGIN)).willReturn(memberId);
        given(authService.getNameById(memberId)).willReturn("Bob");
        // verifyLoginOtpCode is void — default mock does nothing (success path)

        mockMvc.perform(post("/login/otp").with(csrf())
                        .param("token", "login-token")
                        .param("code", "654321"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void verifyLoginOtp_invalid_redirectsBackWithError() throws Exception {
        UUID memberId = UUID.randomUUID();
        given(otpService.resolveOtpToken("login-token", OtpType.LOGIN)).willReturn(memberId);
        given(otpService.recordFailedAttempt("login-token")).willReturn(3);
        willThrow(new IllegalArgumentException("error.otp.invalid"))
                .given(authService).verifyLoginOtpCode(any(), any());

        mockMvc.perform(post("/login/otp").with(csrf())
                        .param("token", "login-token")
                        .param("code", "000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login/otp?token=login-token"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void verifyLoginOtp_locked_redirectsToLogin() throws Exception {
        UUID memberId = UUID.randomUUID();
        given(otpService.resolveOtpToken("login-token", OtpType.LOGIN)).willReturn(memberId);
        given(otpService.recordFailedAttempt("login-token")).willReturn(0);
        willThrow(new IllegalArgumentException("error.otp.invalid"))
                .given(authService).verifyLoginOtpCode(any(), any());

        mockMvc.perform(post("/login/otp").with(csrf())
                        .param("token", "login-token")
                        .param("code", "000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("error"));
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
        // initiatePasswordReset is void — default mock does nothing

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
    void resetPasswordPage_validToken_redirectsToForm() throws Exception {
        given(passwordResetService.isValid(any(), any())).willReturn(true);

        mockMvc.perform(get("/reset-password")
                        .param("mid", UUID.randomUUID().toString())
                        .param("token", "valid-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reset-password/form"));
    }

    @Test
    void resetPasswordForm_withSession_returnsResetView() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("resetMid", UUID.randomUUID().toString());
        session.setAttribute("resetToken", "valid-token");

        mockMvc.perform(get("/reset-password/form").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"));
    }

    @Test
    void resetPasswordForm_withoutSession_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/reset-password/form"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void resetPassword_validSession_redirectsToLoginWithSuccess() throws Exception {
        given(passwordResetService.verify(any(), any())).willReturn(true);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("resetMid", UUID.randomUUID().toString());
        session.setAttribute("resetToken", "valid-token");

        mockMvc.perform(post("/reset-password").with(csrf()).session(session)
                        .param("password", "newpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void resetPassword_withoutSession_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("password", "newpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("error"));
    }
}

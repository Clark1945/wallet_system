package org.side_project.wallet_system.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2User;
import org.side_project.wallet_system.auth.oauth.CustomUserDetails;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.objects.OtpType;
import org.side_project.wallet_system.auth.service.AuthService;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.side_project.wallet_system.auth.service.OtpService;
import org.side_project.wallet_system.config.SessionConstants;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class LoginSuccessHandlerTest {

    @Mock private OtpService otpService;
    @Mock private AuthService authService;
    @Mock private LoginAttemptService loginAttemptService;
    @InjectMocks private LoginSuccessHandler handler;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;
    @Mock private HttpSession session;

    // ── LOCAL (form) login ────────────────────────────────────

    @Test
    void localLogin_clearsAttemptsAndRedirectsToOtp() throws Exception {
        UUID memberId = UUID.randomUUID();
        CustomUserDetails ud = mock(CustomUserDetails.class);
        given(ud.getMemberId()).willReturn(memberId);
        given(ud.getUsername()).willReturn("user@example.com");
        given(authentication.getPrincipal()).willReturn(ud);
        given(otpService.generateOtpToken(memberId, OtpType.LOGIN)).willReturn("otp-token-xyz");

        handler.onAuthenticationSuccess(request, response, authentication);

        then(loginAttemptService).should().clearFailures("user@example.com");
        then(authService).should().sendLoginOtp(memberId, "user@example.com");
        then(response).should().sendRedirect("/login/otp?token=otp-token-xyz");
    }

    @Test
    void localLogin_otpSendFails_redirectsToLoginError() throws Exception {
        CustomUserDetails ud = mock(CustomUserDetails.class);
        given(ud.getMemberId()).willReturn(UUID.randomUUID());
        given(ud.getUsername()).willReturn("fail@example.com");
        given(authentication.getPrincipal()).willReturn(ud);
        willThrow(new RuntimeException("SMTP error")).given(authService).sendLoginOtp(any(), any());

        handler.onAuthenticationSuccess(request, response, authentication);

        then(response).should().sendRedirect("/login?error");
        then(otpService).should(never()).generateOtpToken(any(), any());
    }

    // ── GOOGLE (OAuth2) login ─────────────────────────────────

    @Test
    void googleLogin_setsSessionAttributesAndRedirectsToDashboard() throws Exception {
        UUID memberId = UUID.randomUUID();
        CustomOAuth2User ou = mock(CustomOAuth2User.class);
        given(ou.getMemberId()).willReturn(memberId);
        given(ou.getMemberName()).willReturn("Clark Huang");
        given(authentication.getPrincipal()).willReturn(ou);
        given(request.getSession(true)).willReturn(session);

        handler.onAuthenticationSuccess(request, response, authentication);

        then(session).should().setAttribute(SessionConstants.MEMBER_ID, memberId.toString());
        then(session).should().setAttribute(SessionConstants.MEMBER_NAME, "Clark Huang");
        then(authService).should().updateLastLogin(memberId);
        then(response).should().sendRedirect("/dashboard");
    }

    // ── unknown principal ─────────────────────────────────────

    @Test
    void unknownPrincipal_redirectsToLoginError() throws Exception {
        given(authentication.getPrincipal()).willReturn("some-unexpected-string");

        handler.onAuthenticationSuccess(request, response, authentication);

        then(response).should().sendRedirect("/login?error");
        then(authService).shouldHaveNoInteractions();
    }
}

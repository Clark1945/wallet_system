package org.side_project.wallet_system.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.audit.AuditAction;
import org.side_project.wallet_system.audit.AuditLog;
import org.side_project.wallet_system.audit.AuditResult;
import org.side_project.wallet_system.audit.AuditService;
import org.side_project.wallet_system.auth.service.LoginAttemptService;
import org.side_project.wallet_system.auth.service.OtpService;
import org.side_project.wallet_system.auth.objects.OtpType;
import org.side_project.wallet_system.auth.service.AuthService;
import org.side_project.wallet_system.config.SessionConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OtpService otpService;
    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomUserDetails ud) {
            UUID memberId = ud.getMemberId();
            String email  = ud.getUsername();
            log.info("Login credentials verified (LOCAL): memberId={}", memberId);
            loginAttemptService.clearFailures(email);
            // LOGIN_SUCCESS is audited only once the mandatory OTP second factor passes
            // (AuthFlowService.verifyLoginOtp) — password verification alone is not a completed login.

            try {
                authService.sendLoginOtp(memberId, email);
                String otpToken = otpService.generateOtpToken(memberId, OtpType.LOGIN);
                response.sendRedirect("/login/otp?token=" + otpToken);
            } catch (Exception e) {
                log.error("Failed to send login OTP to {}: {}", email, e.getMessage(), e);
                response.sendRedirect("/login?error");
            }

        } else if (principal instanceof CustomOAuth2User ou) {
            UUID memberId   = ou.getMemberId();
            String memberName = ou.getMemberName();
            log.info("Login success (GOOGLE): memberId={}, name={}", memberId, memberName);

            boolean admin = authService.isAdmin(memberId);
            HttpSession session = request.getSession(true);
            session.setAttribute(SessionConstants.MEMBER_ID,   memberId.toString());
            session.setAttribute(SessionConstants.MEMBER_NAME, memberName);
            session.setAttribute(SessionConstants.IS_ADMIN,    admin);
            authService.updateLastLogin(memberId);

            AuditLog log = AuditLog.builder()
                    .actorId(memberId).actorEmail(authService.getEmailById(memberId))
                    .action(AuditAction.LOGIN_SUCCESS).result(AuditResult.SUCCESS)
                    .targetType("MEMBER").targetId(memberId.toString())
                    .detail("provider=GOOGLE").build();
            auditService.record(log);

            response.sendRedirect(admin ? "/admin" : "/dashboard");

        } else {
            log.warn("Login failed - unknown principal type: {}", principal.getClass().getName());
            response.sendRedirect("/login?error");
        }
    }
}

package org.side_project.wallet_system.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final EmailService emailService;
    private final AuthService authService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomUserDetails ud) {
            UUID memberId   = ud.getMemberId();
            String memberName = ud.getMemberName();
            String email    = ud.getUsername();
            log.info("Login success (LOCAL): memberId={}, name={}", memberId, memberName);

            HttpSession session = request.getSession(true);
            session.setAttribute(SessionConstants.MEMBER_ID,   memberId.toString());
            session.setAttribute(SessionConstants.MEMBER_NAME, memberName);

            try {
                String otp = otpService.generateAndStore(memberId, OtpType.LOGIN);
                emailService.sendLoginOtp(email, otp);
                session.setAttribute(SessionConstants.PENDING_OTP,  true);
                session.setAttribute(SessionConstants.OTP_EMAIL,    email);
                response.sendRedirect("/login/otp");
            } catch (Exception e) {
                log.error("Failed to send login OTP to {}: {}", email, e.getMessage(), e);
                session.invalidate();
                response.sendRedirect("/login?error");
            }

        } else if (principal instanceof CustomOAuth2User ou) {
            UUID memberId   = ou.getMemberId();
            String memberName = ou.getMemberName();
            log.info("Login success (GOOGLE): memberId={}, name={}", memberId, memberName);

            HttpSession session = request.getSession(true);
            session.setAttribute(SessionConstants.MEMBER_ID,   memberId.toString());
            session.setAttribute(SessionConstants.MEMBER_NAME, memberName);
            authService.updateLastLogin(memberId);
            response.sendRedirect("/dashboard");

        } else {
            log.warn("Login failed - unknown principal type: {}", principal.getClass().getName());
            response.sendRedirect("/login?error");
        }
    }
}

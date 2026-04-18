package org.side_project.wallet_system.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import org.side_project.wallet_system.config.SessionConstants;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        UUID memberId;
        String memberName;

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails ud) {
            memberId   = ud.getMemberId();
            memberName = ud.getMemberName();
            log.info("Login success (LOCAL): memberId={}, name={}", memberId, memberName);
        } else if (principal instanceof CustomOAuth2User ou) {
            memberId   = ou.getMemberId();
            memberName = ou.getMemberName();
            log.info("Login success (GOOGLE): memberId={}, name={}", memberId, memberName);
        } else {
            log.warn("Login failed - unknown principal type: {}", principal.getClass().getName());
            response.sendRedirect("/login?error");
            return;
        }

        request.getSession(true).setAttribute(SessionConstants.MEMBER_ID,   memberId.toString());
        request.getSession().setAttribute(SessionConstants.MEMBER_NAME, memberName);
        response.sendRedirect("/dashboard");
    }
}

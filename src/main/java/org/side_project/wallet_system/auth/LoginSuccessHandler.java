package org.side_project.wallet_system.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

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
        } else if (principal instanceof CustomOAuth2User ou) {
            memberId   = ou.getMemberId();
            memberName = ou.getMemberName();
        } else {
            response.sendRedirect("/login?error");
            return;
        }

        request.getSession(true).setAttribute("memberId",    memberId.toString());
        request.getSession().setAttribute("memberName", memberName);
        response.sendRedirect("/dashboard");
    }
}

package org.side_project.wallet_system.config;

import org.side_project.wallet_system.auth.oauth.CustomUserDetails;
import org.side_project.wallet_system.auth.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class TestModeModelAdvice {

    @ModelAttribute("isTestMode")
    public boolean isTestMode(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (authentication.getPrincipal() instanceof CustomUserDetails ud) {
            return AuthService.TEST_EMAIL.equals(ud.getUsername());
        }
        return false;
    }
}

package org.side_project.wallet_system.auth.controller;

import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.auth.service.AuthFlowService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@Profile("!prod")
@RequiredArgsConstructor
public class TestModeController {

    private final AuthFlowService authFlowService;

    @PostMapping("/register/test")
    public String initiateTestMode(RedirectAttributes redirectAttributes, Locale locale) {
        return authFlowService.initiateTestMode(redirectAttributes, locale);
    }
}

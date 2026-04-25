package org.side_project.wallet_system.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class TestModeInitializer {

    @Value("${app.test-email}")
    private String testEmail;
    private final AuthService authService;

    @EventListener(ApplicationReadyEvent.class)
    public void seedTestAccount() {
        authService.ensureTestMemberActive();
        log.info("Test account ready: {} / test1234 / OTP {}", testEmail, "123456");
    }
}

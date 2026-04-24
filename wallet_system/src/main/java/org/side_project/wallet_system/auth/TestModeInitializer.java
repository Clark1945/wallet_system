package org.side_project.wallet_system.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.auth.service.AuthService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class TestModeInitializer {

    private final AuthService authService;

    @EventListener(ApplicationReadyEvent.class)
    public void seedTestAccount() {
        authService.ensureTestMemberActive();
        log.info("Test account ready: {} / test1234 / OTP {}", AuthService.TEST_EMAIL, "123456");
    }
}

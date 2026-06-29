package org.side_project.wallet_system.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Promotes a configured member to admin on startup. Idempotent: skips when the email is blank,
 * the member does not exist, or the member is already an admin. Mirrors {@link TestModeInitializer}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeedInitializer {

    @Value("${app.admin-email:}")
    private String adminEmail;

    private final MemberRepository memberRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedAdmin() {
        if (!StringUtils.hasText(adminEmail)) {
            return;
        }
        memberRepository.findByEmail(adminEmail).ifPresentOrElse(member -> {
            if (member.isAdmin()) {
                log.info("Admin already set: {}", adminEmail);
                return;
            }
            member.setAdmin(true);
            memberRepository.save(member);
            log.info("Promoted member to admin: {}", adminEmail);
        }, () -> log.warn("Admin seed skipped — no member with email {}", adminEmail));
    }
}

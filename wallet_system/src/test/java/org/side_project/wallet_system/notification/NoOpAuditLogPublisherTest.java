package org.side_project.wallet_system.notification;

import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.audit.AuditAction;
import org.side_project.wallet_system.audit.AuditLog;
import org.side_project.wallet_system.audit.AuditResult;

import static org.assertj.core.api.Assertions.assertThatCode;

class NoOpAuditLogPublisherTest {

    @Test
    void publish_isNoOp_andNeverThrows() {
        NoOpAuditLogPublisher publisher = new NoOpAuditLogPublisher();

        assertThatCode(() -> publisher.publish(AuditLog.builder()
                .action(AuditAction.TRANSFER).result(AuditResult.SUCCESS).build()))
                .doesNotThrowAnyException();
    }
}

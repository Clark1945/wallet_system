package org.side_project.wallet_system.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.audit.AuditAction;
import org.side_project.wallet_system.audit.AuditLog;
import org.side_project.wallet_system.audit.AuditResult;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQAuditLogPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @InjectMocks private RabbitMQAuditLogPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "exchange", "wallet.audit.log");
        ReflectionTestUtils.setField(publisher, "routingKey", "audit.log.notification");
    }

    @Test
    void publish_sendsToConfiguredExchangeAndRoutingKey() {
        AuditLog log = AuditLog.builder()
                .action(AuditAction.LOGIN_SUCCESS).result(AuditResult.SUCCESS).build();

        publisher.publish(log);

        verify(rabbitTemplate).convertAndSend("wallet.audit.log", "audit.log.notification", log);
    }

    @Test
    void publish_swallowsBrokerFailure_neverThrows() {
        willThrow(new RuntimeException("broker down"))
                .given(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertThatCode(() -> publisher.publish(AuditLog.builder()
                .action(AuditAction.DEPOSIT_COMPLETED).result(AuditResult.SUCCESS).build()))
                .doesNotThrowAnyException();
    }
}

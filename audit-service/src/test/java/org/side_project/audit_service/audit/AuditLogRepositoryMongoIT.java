package org.side_project.audit_service.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.side_project.audit_service.config.MongoConfig;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saves an AuditLog with a non-null UUID actorId to a real MongoDB (Testcontainers) and reads it
 * back. Guards the uuidRepresentation config: before the fix this threw
 * {@code CodecConfigurationException: The uuidRepresentation has not been specified}.
 *
 * <p>Auto-skipped when Docker is unavailable; runs in CI.
 */
@DataMongoTest
@Import(MongoConfig.class)   // @DataMongoTest doesn't scan @Configuration; pull in the uuidRepresentation customizer
@Testcontainers(disabledWithoutDocker = true)
class AuditLogRepositoryMongoIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private AuditLogRepository repository;

    @Test
    void persistsAndReadsBackAuditLogWithUuidActorId() {
        UUID actorId = UUID.randomUUID();
        repository.save(AuditLog.builder()
                .id("mongo-it-1")
                .actorId(actorId)
                .action(AuditAction.LOGIN_SUCCESS)
                .result(AuditResult.SUCCESS)
                .traceId("trace-mongo-it")
                .build());

        AuditLog found = repository.findById("mongo-it-1").orElseThrow();
        assertThat(found.getActorId()).isEqualTo(actorId);   // UUID round-trips — fails pre-fix
        assertThat(found.getAction()).isEqualTo(AuditAction.LOGIN_SUCCESS);
        assertThat(found.getResult()).isEqualTo(AuditResult.SUCCESS);
    }
}

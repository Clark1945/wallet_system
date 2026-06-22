package org.side_project.audit_service.config;

import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

    /**
     * Encode {@code java.util.UUID} fields (e.g. {@link org.side_project.audit_service.audit.AuditLog#actorId})
     * as BSON binary subtype 4. Without this the driver reports {@code uuidRepresentation=UNSPECIFIED}
     * and {@code save()} throws {@code CodecConfigurationException} for any audit event carrying an actor id.
     *
     * <p>Set on the client settings directly: the declarative {@code spring.mongodb.uuid-representation}
     * property is a no-op under Spring Boot 4. Applies to every Mongo connection, including the
     * Testcontainers {@code @ServiceConnection} URI used by the integration test.
     */
    @Bean
    MongoClientSettingsBuilderCustomizer uuidRepresentationCustomizer() {
        return builder -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }
}

package org.side_project.audit_service.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);

    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);
}

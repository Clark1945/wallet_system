package org.side_project.wallet_system.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);

    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);
}
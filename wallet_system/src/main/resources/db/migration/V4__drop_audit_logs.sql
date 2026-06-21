-- Audit records are no longer stored by wallet_system. AuditService now publishes audit events
-- to RabbitMQ (exchange wallet.audit.log); audit-service consumes them and persists to MongoDB.
-- Drop the now-unused table (its historical rows predate the MongoDB store).
DROP TABLE IF EXISTS audit_logs;

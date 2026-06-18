package org.side_project.wallet_system.audit;

/**
 * Auditable actions recorded in the {@code audit_logs} table.
 * Covers authentication events and every money-moving operation.
 */
public enum AuditAction {
    // ── Authentication ──
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    REGISTER,
    PASSWORD_RESET,

    // ── Deposit ──
    DEPOSIT_INITIATED,
    DEPOSIT_COMPLETED,
    DEPOSIT_FAILED,

    // ── Withdrawal ──
    WITHDRAWAL_INITIATED,
    WITHDRAWAL_COMPLETED,
    WITHDRAWAL_FAILED,

    // ── Transfer ──
    TRANSFER
}
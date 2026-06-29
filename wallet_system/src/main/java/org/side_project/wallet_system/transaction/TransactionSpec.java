package org.side_project.wallet_system.transaction;

import jakarta.persistence.criteria.Predicate;
import org.side_project.wallet_system.config.AppZone;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionSpec {

    private TransactionSpec() {}

    public static Specification<Transaction> filter(UUID walletId,
                                                    TransactionType type,
                                                    LocalDate startDate,
                                                    LocalDate endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.or(
                cb.equal(root.get("fromWalletId"), walletId),
                cb.equal(root.get("toWalletId"), walletId)
            ));

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            // The user picks dates on their wall clock (Asia/Taipei); convert each day boundary to the
            // matching absolute instant so it compares correctly against the UTC-stored createdAt.
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        startDate.atStartOfDay(AppZone.DISPLAY).toInstant()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        endDate.plusDays(1).atStartOfDay(AppZone.DISPLAY).toInstant()));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Admin view: every wallet's transactions (no wallet constraint), optionally narrowed by
     * status, an order number, and a date range.
     *
     * <p>The order number matches either the transaction's own UUID (exact) <em>or</em> the
     * gateway-supplied {@code paymentExternalId} (substring), so an admin can paste either an
     * internal id or a Stripe/SBPS reference. If the input isn't a valid UUID, only
     * {@code paymentExternalId} is matched.
     */
    public static Specification<Transaction> adminFilter(TransactionStatus status,
                                                         String orderNo,
                                                         LocalDate startDate,
                                                         LocalDate endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (orderNo != null && !orderNo.isBlank()) {
                String trimmed = orderNo.trim();
                List<Predicate> idMatches = new ArrayList<>();
                idMatches.add(cb.like(root.get("paymentExternalId"), "%" + trimmed + "%"));
                UUID txId = tryParseUuid(trimmed);
                if (txId != null) {
                    idMatches.add(cb.equal(root.get("id"), txId));
                }
                predicates.add(cb.or(idMatches.toArray(new Predicate[0])));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        startDate.atStartOfDay(AppZone.DISPLAY).toInstant()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        endDate.plusDays(1).atStartOfDay(AppZone.DISPLAY).toInstant()));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

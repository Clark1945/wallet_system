package org.side_project.wallet_system.transaction;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    @Test
    void getCreatedAtLocal_rendersStoredInstantInTaipei() {
        Transaction tx = new Transaction();
        tx.setCreatedAt(Instant.parse("2026-06-28T13:27:30Z")); // 13:27 UTC

        // Asia/Taipei is UTC+8 (no DST) -> 21:27 wall clock
        assertThat(tx.getCreatedAtLocal()).isEqualTo(LocalDateTime.of(2026, 6, 28, 21, 27, 30));
    }

    @Test
    void getCreatedAtLocal_nullWhenUnset() {
        assertThat(new Transaction().getCreatedAtLocal()).isNull();
    }
}

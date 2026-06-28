package org.side_project.wallet_system.config;

import java.time.ZoneId;

/**
 * Single source of truth for the application's display time zone.
 *
 * <p>Event timestamps are stored as absolute instants ({@link java.time.Instant} / {@code timestamptz}
 * / BSON Date, all UTC-anchored). The wall-clock the user sees is produced by converting those
 * instants to this zone at the edges only (view layer, date-range filters). Storage stays UTC.
 */
public final class AppZone {

    /** The user-facing zone for this (single-region) deployment. */
    public static final ZoneId DISPLAY = ZoneId.of("Asia/Taipei");

    private AppZone() {}
}

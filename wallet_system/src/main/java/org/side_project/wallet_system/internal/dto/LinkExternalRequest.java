package org.side_project.wallet_system.internal.dto;

import java.util.UUID;

public record LinkExternalRequest(UUID transactionId, String externalId) {}

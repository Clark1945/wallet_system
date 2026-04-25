package org.side_project.payment_service.client.dto;

import java.util.UUID;

public record LinkExternalRequest(UUID transactionId, String externalId) {}

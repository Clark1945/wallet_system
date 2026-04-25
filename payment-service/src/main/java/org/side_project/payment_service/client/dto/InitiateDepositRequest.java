package org.side_project.payment_service.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiateDepositRequest(UUID memberId, BigDecimal amount, String notifyEmail) {}

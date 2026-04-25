package org.side_project.payment_service.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentTokenData(UUID memberId, BigDecimal amount, String method, String notifyEmail) {}

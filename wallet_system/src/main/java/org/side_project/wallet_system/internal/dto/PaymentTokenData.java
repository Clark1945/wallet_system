package org.side_project.wallet_system.internal.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentTokenData(UUID memberId, BigDecimal amount, String method, String notifyEmail) {}

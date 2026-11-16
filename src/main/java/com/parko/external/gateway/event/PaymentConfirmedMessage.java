package com.parko.external.gateway.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentConfirmedMessage(
        UUID operationId,
        UUID userId,
        BigDecimal amount
) {
}

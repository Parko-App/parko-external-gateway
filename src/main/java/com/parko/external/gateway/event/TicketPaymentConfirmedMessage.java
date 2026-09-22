package com.parko.external.gateway.event;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketPaymentConfirmedMessage(
        UUID parkingSessionId,
        BigDecimal amount
) {
}

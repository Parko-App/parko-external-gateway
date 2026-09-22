package com.parko.external.gateway.event;

import java.util.UUID;

public record TicketPaymentFailedMessage(
        UUID parkingSessionId,
        String reason
) {
}

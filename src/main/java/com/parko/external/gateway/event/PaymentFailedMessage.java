package com.parko.external.gateway.event;

import java.util.UUID;

public record PaymentFailedMessage(
        UUID operationId,
        String reason
) {
}

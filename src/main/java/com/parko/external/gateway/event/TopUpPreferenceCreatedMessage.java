package com.parko.external.gateway.event;

import java.util.UUID;

public record TopUpPreferenceCreatedMessage(
        UUID operationId,
        String preferenceId
) {
}

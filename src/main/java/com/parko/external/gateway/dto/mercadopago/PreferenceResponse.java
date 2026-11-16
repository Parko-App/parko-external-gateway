package com.parko.external.gateway.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreferenceResponse(
        String id,
        @JsonProperty("init_point") String initPoint,
        @JsonProperty("sandbox_init_point") String sandboxInitPoint
) {
}

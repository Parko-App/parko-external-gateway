package com.parko.external.gateway.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PreferenceRequest(
        List<PreferenceItem> items,
        @JsonProperty("external_reference") String externalReference
) {
}

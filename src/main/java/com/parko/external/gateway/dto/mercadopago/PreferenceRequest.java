package com.parko.external.gateway.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PreferenceRequest(
        List<PreferenceItem> items,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("notification_url") String notificationUrl,
        @JsonProperty("statement_descriptor") String statementDescriptor,
        Map<String, String> metadata
) {
}

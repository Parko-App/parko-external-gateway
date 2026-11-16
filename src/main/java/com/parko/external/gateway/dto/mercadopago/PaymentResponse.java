package com.parko.external.gateway.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

public record PaymentResponse(
        Long id,
        String status,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("transaction_amount") BigDecimal transactionAmount,
        Map<String, String> metadata
) {
}

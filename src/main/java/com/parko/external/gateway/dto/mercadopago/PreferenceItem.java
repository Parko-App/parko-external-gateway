package com.parko.external.gateway.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record PreferenceItem(
        String title,
        Integer quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("currency_id") String currencyId
) {
}

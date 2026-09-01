package com.parko.external.gateway.dto.mercadopago;

public record MercadoPagoNotification(
        String id,
        String action,
        String type,
        Data data
) {
    public record Data(String id) {
    }
}

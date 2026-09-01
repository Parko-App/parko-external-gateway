package com.parko.external.gateway.service;

import com.parko.external.gateway.client.MercadoPagoClient;
import com.parko.external.gateway.dto.mercadopago.PreferenceItem;
import com.parko.external.gateway.dto.mercadopago.PreferenceRequest;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.event.TopUpMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MercadoPagoService {

    private static final String CURRENCY_ID = "ARS";
    private static final String STATEMENT_DESCRIPTOR = "PARKOAPP";

    private final MercadoPagoClient mercadoPagoClient;

    @Value("${mercadopago.notification-url:}")
    private String notificationUrl;

    public MercadoPagoService(MercadoPagoClient mercadoPagoClient) {
        this.mercadoPagoClient = mercadoPagoClient;
    }

    public PreferenceResponse createTopUpPreference(TopUpMessage message) {
        PreferenceItem item = new PreferenceItem(
                "Recarga de saldo Parko",
                1,
                message.amount(),
                CURRENCY_ID
        );
        PreferenceRequest request = new PreferenceRequest(
                List.of(item),
                message.operationId().toString(),
                notificationUrl == null || notificationUrl.isBlank() ? null : notificationUrl,
                STATEMENT_DESCRIPTOR,
                Map.of("user_id", message.userId().toString())
        );
        return mercadoPagoClient.createPreference(request);
    }
}

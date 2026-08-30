package com.parko.external.gateway.service;

import com.parko.external.gateway.client.MercadoPagoClient;
import com.parko.external.gateway.dto.mercadopago.PreferenceItem;
import com.parko.external.gateway.dto.mercadopago.PreferenceRequest;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.event.TopUpMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MercadoPagoService {

    private static final String CURRENCY_ID = "ARS";

    private final MercadoPagoClient mercadoPagoClient;

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
                message.operationId().toString()
        );
        return mercadoPagoClient.createPreference(request);
    }
}

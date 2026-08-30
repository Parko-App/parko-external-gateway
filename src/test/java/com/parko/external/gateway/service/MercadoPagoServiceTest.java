package com.parko.external.gateway.service;

import com.parko.external.gateway.client.MercadoPagoClient;
import com.parko.external.gateway.dto.mercadopago.PreferenceRequest;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.event.TopUpMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoServiceTest {

    @Mock
    private MercadoPagoClient mercadoPagoClient;

    private MercadoPagoService mercadoPagoService;

    @BeforeEach
    void setUp() {
        mercadoPagoService = new MercadoPagoService(mercadoPagoClient);
    }

    @Test
    void createTopUpPreference_buildsRequestFromMessageAndReturnsClientResponse() {
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(150);
        TopUpMessage message = new TopUpMessage(operationId, UUID.randomUUID(), amount, Instant.now());
        PreferenceResponse expectedResponse = new PreferenceResponse("pref-1", "https://mp.com/init", "https://mp.com/sandbox");
        when(mercadoPagoClient.createPreference(any(PreferenceRequest.class))).thenReturn(expectedResponse);

        PreferenceResponse response = mercadoPagoService.createTopUpPreference(message);

        assertThat(response).isEqualTo(expectedResponse);

        ArgumentCaptor<PreferenceRequest> captor = ArgumentCaptor.forClass(PreferenceRequest.class);
        verify(mercadoPagoClient).createPreference(captor.capture());
        PreferenceRequest sentRequest = captor.getValue();
        assertThat(sentRequest.externalReference()).isEqualTo(operationId.toString());
        assertThat(sentRequest.items()).hasSize(1);
        assertThat(sentRequest.items().get(0).title()).isEqualTo("Recarga de saldo Parko");
        assertThat(sentRequest.items().get(0).quantity()).isEqualTo(1);
        assertThat(sentRequest.items().get(0).unitPrice()).isEqualByComparingTo(amount);
        assertThat(sentRequest.items().get(0).currencyId()).isEqualTo("ARS");
    }
}

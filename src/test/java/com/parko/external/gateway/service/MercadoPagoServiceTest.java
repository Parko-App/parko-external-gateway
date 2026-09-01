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
import org.springframework.test.util.ReflectionTestUtils;

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

    private MercadoPagoService service;

    @BeforeEach
    void setUp() {
        service = new MercadoPagoService(mercadoPagoClient);
    }

    @Test
    void createTopUpPreference_buildsRequestFromMessage() {
        UUID operationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(150);
        TopUpMessage message = new TopUpMessage(operationId, userId, amount, Instant.now());
        when(mercadoPagoClient.createPreference(any(PreferenceRequest.class)))
                .thenReturn(new PreferenceResponse("pref-1", "https://mp.com/init", "https://mp.com/sandbox"));

        PreferenceResponse response = service.createTopUpPreference(message);

        assertThat(response.initPoint()).isEqualTo("https://mp.com/init");

        ArgumentCaptor<PreferenceRequest> captor = ArgumentCaptor.forClass(PreferenceRequest.class);
        verify(mercadoPagoClient).createPreference(captor.capture());
        PreferenceRequest sentRequest = captor.getValue();
        assertThat(sentRequest.externalReference()).isEqualTo(operationId.toString());
        assertThat(sentRequest.items()).hasSize(1);
        assertThat(sentRequest.items().get(0).title()).isEqualTo("Recarga de saldo Parko");
        assertThat(sentRequest.items().get(0).unitPrice()).isEqualByComparingTo(amount);
        assertThat(sentRequest.metadata()).containsEntry("user_id", userId.toString());
        assertThat(sentRequest.statementDescriptor()).isEqualTo("PARKOAPP");
    }

    @Test
    void createTopUpPreference_setsNotificationUrl_whenConfigured() {
        ReflectionTestUtils.setField(service, "notificationUrl", "https://gateway.example.com/webhooks/mercadopago");
        TopUpMessage message = new TopUpMessage(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        when(mercadoPagoClient.createPreference(any(PreferenceRequest.class)))
                .thenReturn(new PreferenceResponse("pref-1", "https://mp.com/init", "https://mp.com/sandbox"));

        service.createTopUpPreference(message);

        ArgumentCaptor<PreferenceRequest> captor = ArgumentCaptor.forClass(PreferenceRequest.class);
        verify(mercadoPagoClient).createPreference(captor.capture());
        assertThat(captor.getValue().notificationUrl()).isEqualTo("https://gateway.example.com/webhooks/mercadopago");
    }
}

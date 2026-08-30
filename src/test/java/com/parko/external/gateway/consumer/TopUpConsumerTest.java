package com.parko.external.gateway.consumer;

import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.event.TopUpMessage;
import com.parko.external.gateway.service.MercadoPagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopUpConsumerTest {

    @Mock
    private MercadoPagoService mercadoPagoService;

    private TopUpConsumer topUpConsumer;

    @BeforeEach
    void setUp() {
        topUpConsumer = new TopUpConsumer(mercadoPagoService);
    }

    @Test
    void onTopUp_delegatesToMercadoPagoService() {
        TopUpMessage message = new TopUpMessage(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        PreferenceResponse response = new PreferenceResponse("pref-1", "https://mp.com/init", "https://mp.com/sandbox");
        when(mercadoPagoService.createTopUpPreference(eq(message))).thenReturn(response);

        topUpConsumer.onTopUp(message);

        verify(mercadoPagoService).createTopUpPreference(message);
    }
}

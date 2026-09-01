package com.parko.external.gateway.consumer;

import com.parko.external.gateway.config.RabbitConfig;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.event.TopUpMessage;
import com.parko.external.gateway.event.TopUpPreferenceCreatedMessage;
import com.parko.external.gateway.service.MercadoPagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopUpConsumerTest {

    @Mock
    private MercadoPagoService mercadoPagoService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TopUpConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TopUpConsumer(mercadoPagoService, rabbitTemplate);
    }

    @Test
    void onTopUp_delegatesToServiceAndPublishesPreferenceCreated() {
        TopUpMessage message = new TopUpMessage(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        when(mercadoPagoService.createTopUpPreference(message))
                .thenReturn(new PreferenceResponse("pref-1", "https://mp.com/init", "https://mp.com/sandbox"));

        consumer.onTopUp(message);

        verify(mercadoPagoService).createTopUpPreference(message);
        verify(rabbitTemplate).convertAndSend(
                RabbitConfig.BALANCE_EXCHANGE,
                RabbitConfig.TOPUP_PREFERENCE_CREATED_ROUTING_KEY,
                new TopUpPreferenceCreatedMessage(message.operationId(), "pref-1")
        );
    }
}

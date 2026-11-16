package com.parko.external.gateway.consumer;

import com.parko.external.gateway.config.RabbitConfig;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.event.PaymentFailedMessage;
import com.parko.external.gateway.event.TopUpMessage;
import com.parko.external.gateway.event.TopUpPreferenceCreatedMessage;
import com.parko.external.gateway.service.MercadoPagoService;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void onTopUp_publishesFailure_whenMercadoPagoRejectsRequest() {
        TopUpMessage message = new TopUpMessage(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        FeignException feignException = mock(FeignException.class);
        when(mercadoPagoService.createTopUpPreference(message)).thenThrow(feignException);

        consumer.onTopUp(message);

        verify(rabbitTemplate).convertAndSend(
                RabbitConfig.BALANCE_EXCHANGE,
                RabbitConfig.PAYMENT_FAILED_ROUTING_KEY,
                new PaymentFailedMessage(message.operationId(), "mercadopago_preference_error")
        );
        verify(rabbitTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitConfig.BALANCE_EXCHANGE),
                org.mockito.ArgumentMatchers.eq(RabbitConfig.TOPUP_PREFERENCE_CREATED_ROUTING_KEY),
                org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void onTopUp_publishesFailure_whenUnexpectedExceptionOccurs() {
        TopUpMessage message = new TopUpMessage(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        when(mercadoPagoService.createTopUpPreference(message)).thenThrow(new IllegalStateException("bug"));

        consumer.onTopUp(message);

        verify(rabbitTemplate).convertAndSend(
                RabbitConfig.BALANCE_EXCHANGE,
                RabbitConfig.PAYMENT_FAILED_ROUTING_KEY,
                new PaymentFailedMessage(message.operationId(), "mercadopago_preference_error")
        );
    }

    @Test
    void onTopUp_swallowsBrokerFailure_whenPreferenceCreatedButNotificationPublishFails() {
        TopUpMessage message = new TopUpMessage(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        when(mercadoPagoService.createTopUpPreference(message))
                .thenReturn(new PreferenceResponse("pref-1", "https://mp.com/init", "https://mp.com/sandbox"));
        doThrow(new AmqpException("broker down")).when(rabbitTemplate).convertAndSend(
                RabbitConfig.BALANCE_EXCHANGE,
                RabbitConfig.TOPUP_PREFERENCE_CREATED_ROUTING_KEY,
                new TopUpPreferenceCreatedMessage(message.operationId(), "pref-1")
        );

        assertThatCode(() -> consumer.onTopUp(message)).doesNotThrowAnyException();
    }
}

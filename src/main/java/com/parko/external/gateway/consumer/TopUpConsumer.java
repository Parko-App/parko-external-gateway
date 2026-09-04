package com.parko.external.gateway.consumer;

import com.parko.external.gateway.config.RabbitConfig;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.event.PaymentFailedMessage;
import com.parko.external.gateway.event.TopUpMessage;
import com.parko.external.gateway.event.TopUpPreferenceCreatedMessage;
import com.parko.external.gateway.service.MercadoPagoService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TopUpConsumer {

    private static final String PREFERENCE_CREATION_FAILED_REASON = "mercadopago_preference_error";

    private static final Logger log = LoggerFactory.getLogger(TopUpConsumer.class);

    private final MercadoPagoService mercadoPagoService;
    private final RabbitTemplate rabbitTemplate;

    public TopUpConsumer(MercadoPagoService mercadoPagoService, RabbitTemplate rabbitTemplate) {
        this.mercadoPagoService = mercadoPagoService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitConfig.TOPUP_QUEUE)
    public void onTopUp(TopUpMessage message) {
        PreferenceResponse preference;
        try {
            preference = mercadoPagoService.createTopUpPreference(message);
        } catch (FeignException e) {
            log.warn("Mercado Pago rechazo la creacion de preferencia para operationId={} (status={})",
                    message.operationId(), e.status());
            publishFailure(message);
            return;
        } catch (RuntimeException e) {
            log.error("Fallo inesperado creando la preferencia para operationId={}", message.operationId(), e);
            publishFailure(message);
            return;
        }

        log.info("Preferencia creada para operationId={}",
                message.operationId());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.BALANCE_EXCHANGE,
                    RabbitConfig.TOPUP_PREFERENCE_CREATED_ROUTING_KEY,
                    new TopUpPreferenceCreatedMessage(message.operationId(), preference.id())
            );
        } catch (AmqpException e) {
            log.error("Preferencia {} creada en Mercado Pago para operationId={} pero no se pudo notificar a balance-service, requiere reconciliacion manual",
                    preference.id(), message.operationId(), e);
        }
    }

    private void publishFailure(TopUpMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.BALANCE_EXCHANGE,
                RabbitConfig.PAYMENT_FAILED_ROUTING_KEY,
                new PaymentFailedMessage(message.operationId(), PREFERENCE_CREATION_FAILED_REASON)
        );
    }
}

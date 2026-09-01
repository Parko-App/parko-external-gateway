package com.parko.external.gateway.consumer;

import com.parko.external.gateway.config.RabbitConfig;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.event.TopUpMessage;
import com.parko.external.gateway.event.TopUpPreferenceCreatedMessage;
import com.parko.external.gateway.service.MercadoPagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TopUpConsumer {

    private static final Logger log = LoggerFactory.getLogger(TopUpConsumer.class);

    private final MercadoPagoService mercadoPagoService;
    private final RabbitTemplate rabbitTemplate;

    public TopUpConsumer(MercadoPagoService mercadoPagoService, RabbitTemplate rabbitTemplate) {
        this.mercadoPagoService = mercadoPagoService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitConfig.TOPUP_QUEUE)
    public void onTopUp(TopUpMessage message) {
        PreferenceResponse preference = mercadoPagoService.createTopUpPreference(message);
        log.info("Preferencia creada para operationId={}",
                message.operationId());

        rabbitTemplate.convertAndSend(
                RabbitConfig.BALANCE_EXCHANGE,
                RabbitConfig.TOPUP_PREFERENCE_CREATED_ROUTING_KEY,
                new TopUpPreferenceCreatedMessage(message.operationId(), preference.id())
        );
    }
}

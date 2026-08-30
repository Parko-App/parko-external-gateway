package com.parko.external.gateway.consumer;

import com.parko.external.gateway.config.RabbitConfig;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.event.TopUpMessage;
import com.parko.external.gateway.service.MercadoPagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TopUpConsumer {

    private static final Logger log = LoggerFactory.getLogger(TopUpConsumer.class);

    private final MercadoPagoService mercadoPagoService;

    public TopUpConsumer(MercadoPagoService mercadoPagoService) {
        this.mercadoPagoService = mercadoPagoService;
    }

    @RabbitListener(queues = RabbitConfig.TOPUP_QUEUE)
    public void onTopUp(TopUpMessage message) {
        PreferenceResponse preference = mercadoPagoService.createTopUpPreference(message);
        log.info("Preferencia creada para operationId={}: init_point={}", message.operationId(), preference.initPoint());
    }
}

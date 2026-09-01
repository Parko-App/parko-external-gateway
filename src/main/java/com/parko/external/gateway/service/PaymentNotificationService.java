package com.parko.external.gateway.service;

import com.parko.external.gateway.client.MercadoPagoClient;
import com.parko.external.gateway.config.RabbitConfig;
import com.parko.external.gateway.dto.mercadopago.MercadoPagoNotification;
import com.parko.external.gateway.dto.mercadopago.PaymentResponse;
import com.parko.external.gateway.event.PaymentConfirmedMessage;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentNotificationService {

    private static final String PAYMENT_TYPE = "payment";
    private static final String APPROVED_STATUS = "approved";

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationService.class);

    private final MercadoPagoClient mercadoPagoClient;
    private final RabbitTemplate rabbitTemplate;

    public PaymentNotificationService(MercadoPagoClient mercadoPagoClient, RabbitTemplate rabbitTemplate) {
        this.mercadoPagoClient = mercadoPagoClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void handle(MercadoPagoNotification notification, String dataId) {
        if (!PAYMENT_TYPE.equals(notification.type())) {
            return;
        }

        PaymentResponse payment;
        try {
            payment = mercadoPagoClient.getPayment(dataId);
        } catch (FeignException e) {
            log.warn("No se pudo consultar el pago {} en Mercado Pago (status={}), no se acredita saldo", dataId, e.status());
            return;
        }
        if (!APPROVED_STATUS.equals(payment.status())) {
            log.info("Pago {} no aprobado todavia (status={}), no se acredita saldo", dataId, payment.status());
            return;
        }

        PaymentConfirmedMessage message = new PaymentConfirmedMessage(
                UUID.fromString(payment.externalReference()),
                UUID.fromString(payment.metadata().get("user_id")),
                payment.transactionAmount()
        );
        rabbitTemplate.convertAndSend(RabbitConfig.BALANCE_EXCHANGE, RabbitConfig.PAYMENT_CONFIRMED_ROUTING_KEY, message);
        log.info("Pago {} aprobado, publicado evento de credito para operationId={}", dataId, message.operationId());
    }
}

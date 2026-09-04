package com.parko.external.gateway.service;

import com.parko.external.gateway.client.MercadoPagoClient;
import com.parko.external.gateway.config.RabbitConfig;
import com.parko.external.gateway.dto.mercadopago.MercadoPagoNotification;
import com.parko.external.gateway.dto.mercadopago.PaymentResponse;
import com.parko.external.gateway.event.PaymentConfirmedMessage;
import com.parko.external.gateway.event.PaymentFailedMessage;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class PaymentNotificationService {

    private static final String PAYMENT_TYPE = "payment";
    private static final String APPROVED_STATUS = "approved";
    private static final Set<String> TERMINAL_FAILED_STATUSES = Set.of("rejected", "cancelled");

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

        if (APPROVED_STATUS.equals(payment.status())) {
            PaymentConfirmedMessage message = new PaymentConfirmedMessage(
                    UUID.fromString(payment.externalReference()),
                    UUID.fromString(payment.metadata().get("user_id")),
                    payment.transactionAmount()
            );
            rabbitTemplate.convertAndSend(RabbitConfig.BALANCE_EXCHANGE, RabbitConfig.PAYMENT_CONFIRMED_ROUTING_KEY, message);
            log.info("Pago {} aprobado, publicado evento de credito para operationId={}", dataId, message.operationId());
            return;
        }

        if (TERMINAL_FAILED_STATUSES.contains(payment.status())) {
            PaymentFailedMessage message = new PaymentFailedMessage(
                    UUID.fromString(payment.externalReference()),
                    payment.status()
            );
            rabbitTemplate.convertAndSend(RabbitConfig.BALANCE_EXCHANGE, RabbitConfig.PAYMENT_FAILED_ROUTING_KEY, message);
            log.info("Pago {} con estado terminal fallido (status={}), publicado evento de fallo para operationId={}",
                    dataId, payment.status(), message.operationId());
            return;
        }

        log.info("Pago {} todavia no es terminal (status={}), se espera otra notificacion", dataId, payment.status());
    }
}

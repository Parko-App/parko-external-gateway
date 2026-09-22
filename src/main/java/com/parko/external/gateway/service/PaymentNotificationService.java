package com.parko.external.gateway.service;

import com.parko.external.gateway.client.MercadoPagoClient;
import com.parko.external.gateway.config.RabbitConfig;
import com.parko.external.gateway.dto.mercadopago.MercadoPagoNotification;
import com.parko.external.gateway.dto.mercadopago.PaymentResponse;
import com.parko.external.gateway.event.PaymentConfirmedMessage;
import com.parko.external.gateway.event.PaymentFailedMessage;
import com.parko.external.gateway.event.TicketPaymentConfirmedMessage;
import com.parko.external.gateway.event.TicketPaymentFailedMessage;
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
    private static final String DOMAIN_METADATA_KEY = "domain";
    private static final String TICKET_DOMAIN = "ticket";

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
            log.warn("No se pudo consultar el pago {} en Mercado Pago (status={}), no se procesa", dataId, e.status());
            return;
        }

        boolean isTicketPayment = TICKET_DOMAIN.equals(payment.metadata().get(DOMAIN_METADATA_KEY));

        if (APPROVED_STATUS.equals(payment.status())) {
            if (isTicketPayment) {
                handleTicketConfirmed(payment, dataId);
            } else {
                handleBalanceConfirmed(payment, dataId);
            }
            return;
        }

        if (TERMINAL_FAILED_STATUSES.contains(payment.status())) {
            if (isTicketPayment) {
                handleTicketFailed(payment, dataId);
            } else {
                handleBalanceFailed(payment, dataId);
            }
            return;
        }

        log.info("Pago {} todavia no es terminal (status={}), se espera otra notificacion", dataId, payment.status());
    }

    private void handleBalanceConfirmed(PaymentResponse payment, String dataId) {
        PaymentConfirmedMessage message = new PaymentConfirmedMessage(
                UUID.fromString(payment.externalReference()),
                UUID.fromString(payment.metadata().get("user_id")),
                payment.transactionAmount()
        );
        rabbitTemplate.convertAndSend(RabbitConfig.BALANCE_EXCHANGE, RabbitConfig.PAYMENT_CONFIRMED_ROUTING_KEY, message);
        log.info("Pago {} aprobado, publicado evento de credito para operationId={}", dataId, message.operationId());
    }

    private void handleBalanceFailed(PaymentResponse payment, String dataId) {
        PaymentFailedMessage message = new PaymentFailedMessage(
                UUID.fromString(payment.externalReference()),
                payment.status()
        );
        rabbitTemplate.convertAndSend(RabbitConfig.BALANCE_EXCHANGE, RabbitConfig.PAYMENT_FAILED_ROUTING_KEY, message);
        log.info("Pago {} con estado terminal fallido (status={}), publicado evento de fallo para operationId={}",
                dataId, payment.status(), message.operationId());
    }

    private void handleTicketConfirmed(PaymentResponse payment, String dataId) {
        TicketPaymentConfirmedMessage message = new TicketPaymentConfirmedMessage(
                UUID.fromString(payment.externalReference()),
                payment.transactionAmount()
        );
        rabbitTemplate.convertAndSend(RabbitConfig.ACCESS_EXCHANGE, RabbitConfig.ACCESS_PAYMENT_CONFIRMED_ROUTING_KEY, message);
        log.info("Pago {} aprobado, publicado evento de ticket pagado para parkingSessionId={}", dataId, message.parkingSessionId());
    }

    private void handleTicketFailed(PaymentResponse payment, String dataId) {
        TicketPaymentFailedMessage message = new TicketPaymentFailedMessage(
                UUID.fromString(payment.externalReference()),
                payment.status()
        );
        rabbitTemplate.convertAndSend(RabbitConfig.ACCESS_EXCHANGE, RabbitConfig.ACCESS_PAYMENT_FAILED_ROUTING_KEY, message);
        log.info("Pago {} con estado terminal fallido (status={}), publicado evento de fallo para parkingSessionId={}",
                dataId, payment.status(), message.parkingSessionId());
    }
}

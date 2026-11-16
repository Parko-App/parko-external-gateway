package com.parko.external.gateway.controller;

import com.parko.external.gateway.dto.mercadopago.MercadoPagoNotification;
import com.parko.external.gateway.security.MercadoPagoWebhookSignatureValidator;
import com.parko.external.gateway.service.PaymentNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoWebhookSignatureValidator signatureValidator;
    private final PaymentNotificationService paymentNotificationService;

    public MercadoPagoWebhookController(MercadoPagoWebhookSignatureValidator signatureValidator,
                                         PaymentNotificationService paymentNotificationService) {
        this.signatureValidator = signatureValidator;
        this.paymentNotificationService = paymentNotificationService;
    }

    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Void> receiveNotification(
            @RequestHeader("x-signature") String xSignature,
            @RequestHeader("x-request-id") String xRequestId,
            @RequestParam("data.id") String dataId,
            @RequestBody MercadoPagoNotification notification
    ) {
        if (!signatureValidator.isValid(xSignature, xRequestId, dataId)) {
            log.warn("Firma invalida en notificacion de Mercado Pago, dataId={}, notificationId={}, xSignature={}, xRequestId={}",
                    dataId, notification.id(), xSignature, xRequestId);
            return ResponseEntity.status(401).build();
        }

        log.info("Notificacion de Mercado Pago recibida: type={}, action={}, dataId={}",
                notification.type(), notification.action(), dataId);

        paymentNotificationService.handle(notification, dataId);

        return ResponseEntity.ok().build();
    }
}

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.AmqpException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationServiceTest {

    @Mock
    private MercadoPagoClient mercadoPagoClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private PaymentDeduplicator paymentDeduplicator;

    private PaymentNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentNotificationService(mercadoPagoClient, rabbitTemplate, paymentDeduplicator);
        // Por defecto el pago se reclama como "primera vez"; los tests de duplicados lo pisan.
        lenient().when(paymentDeduplicator.markIfFirst(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void handle_skipsLookup_whenTypeIsNotPayment() {
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "plan.created", "plan", null);

        service.handle(notification, "123456");

        verify(mercadoPagoClient, never()).getPayment(anyString());
    }

    @Test
    void handle_skipsPublish_whenPaymentNotApproved() {
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456"))
                .thenReturn(new PaymentResponse(999L, "pending", UUID.randomUUID().toString(), BigDecimal.TEN, Map.of()));

        service.handle(notification, "123456");

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void handle_publishesCreditEvent_whenPaymentApproved() {
        UUID operationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(150);
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456")).thenReturn(new PaymentResponse(
                999L, "approved", operationId.toString(), amount, Map.of("user_id", userId.toString())
        ));

        service.handle(notification, "123456");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.BALANCE_EXCHANGE),
                eq(RabbitConfig.PAYMENT_CONFIRMED_ROUTING_KEY),
                eq(new PaymentConfirmedMessage(operationId, userId, amount))
        );
    }

    @Test
    void handle_publishesFailureEvent_whenPaymentRejected() {
        UUID operationId = UUID.randomUUID();
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456"))
                .thenReturn(new PaymentResponse(999L, "rejected", operationId.toString(), BigDecimal.TEN, Map.of()));

        service.handle(notification, "123456");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.BALANCE_EXCHANGE),
                eq(RabbitConfig.PAYMENT_FAILED_ROUTING_KEY),
                eq(new PaymentFailedMessage(operationId, "rejected"))
        );
    }

    @Test
    void handle_publishesFailureEvent_whenPaymentCancelled() {
        UUID operationId = UUID.randomUUID();
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456"))
                .thenReturn(new PaymentResponse(999L, "cancelled", operationId.toString(), BigDecimal.TEN, Map.of()));

        service.handle(notification, "123456");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.BALANCE_EXCHANGE),
                eq(RabbitConfig.PAYMENT_FAILED_ROUTING_KEY),
                eq(new PaymentFailedMessage(operationId, "cancelled"))
        );
    }

    @Test
    void handle_publishesTicketPaidEvent_whenPaymentApprovedForTicketDomain() {
        UUID parkingSessionId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(3000);
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456")).thenReturn(new PaymentResponse(
                999L, "approved", parkingSessionId.toString(), amount, Map.of("domain", "ticket")
        ));

        service.handle(notification, "123456");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.ACCESS_EXCHANGE),
                eq(RabbitConfig.ACCESS_PAYMENT_CONFIRMED_ROUTING_KEY),
                eq(new TicketPaymentConfirmedMessage(parkingSessionId, amount))
        );
        verify(rabbitTemplate, never()).convertAndSend(eq(RabbitConfig.BALANCE_EXCHANGE), anyString(), any(Object.class));
    }

    @Test
    void handle_publishesTicketFailedEvent_whenPaymentRejectedForTicketDomain() {
        UUID parkingSessionId = UUID.randomUUID();
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456")).thenReturn(new PaymentResponse(
                999L, "rejected", parkingSessionId.toString(), BigDecimal.TEN, Map.of("domain", "ticket")
        ));

        service.handle(notification, "123456");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.ACCESS_EXCHANGE),
                eq(RabbitConfig.ACCESS_PAYMENT_FAILED_ROUTING_KEY),
                eq(new TicketPaymentFailedMessage(parkingSessionId, "rejected"))
        );
        verify(rabbitTemplate, never()).convertAndSend(eq(RabbitConfig.BALANCE_EXCHANGE), anyString(), any(Object.class));
    }

    @Test
    void handle_claimsThePaymentByIdAndStatus_beforePublishing() {
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456")).thenReturn(new PaymentResponse(
                999L, "approved", UUID.randomUUID().toString(), BigDecimal.TEN, Map.of("user_id", UUID.randomUUID().toString())
        ));

        service.handle(notification, "123456");

        verify(paymentDeduplicator).markIfFirst("999", "approved");
    }

    @Test
    void handle_doesNotPublishAgain_whenPaymentWasAlreadyProcessed() {
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456")).thenReturn(new PaymentResponse(
                999L, "approved", UUID.randomUUID().toString(), BigDecimal.TEN, Map.of("user_id", UUID.randomUUID().toString())
        ));
        when(paymentDeduplicator.markIfFirst("999", "approved")).thenReturn(false);

        service.handle(notification, "123456");

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void handle_doesNotPublishAgain_whenRejectedPaymentWasAlreadyProcessed() {
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456"))
                .thenReturn(new PaymentResponse(999L, "rejected", UUID.randomUUID().toString(), BigDecimal.TEN, Map.of()));
        when(paymentDeduplicator.markIfFirst("999", "rejected")).thenReturn(false);

        service.handle(notification, "123456");

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void handle_usesDataIdAsKey_whenPaymentHasNoId() {
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456")).thenReturn(new PaymentResponse(
                null, "approved", UUID.randomUUID().toString(), BigDecimal.TEN, Map.of("user_id", UUID.randomUUID().toString())
        ));

        service.handle(notification, "123456");

        verify(paymentDeduplicator).markIfFirst("123456", "approved");
    }

    @Test
    void handle_releasesTheClaimAndRethrows_whenPublishingFails() {
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456")).thenReturn(new PaymentResponse(
                999L, "approved", UUID.randomUUID().toString(), BigDecimal.TEN, Map.of("user_id", UUID.randomUUID().toString())
        ));
        doThrow(new AmqpException("rabbit caido")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatThrownBy(() -> service.handle(notification, "123456")).isInstanceOf(AmqpException.class);

        verify(paymentDeduplicator).release("999", "approved");
    }

    @Test
    void handle_doesNotTouchTheDeduplicator_whenPaymentIsNotTerminal() {
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        when(mercadoPagoClient.getPayment("123456"))
                .thenReturn(new PaymentResponse(999L, "pending", UUID.randomUUID().toString(), BigDecimal.TEN, Map.of()));

        service.handle(notification, "123456");

        verify(paymentDeduplicator, never()).markIfFirst(anyString(), anyString());
    }

    @Test
    void handle_skipsPublish_whenPaymentLookupFails() {
        MercadoPagoNotification notification = new MercadoPagoNotification("1", "payment.updated", "payment", null);
        FeignException notFound = mock(FeignException.class);
        when(mercadoPagoClient.getPayment("123456")).thenThrow(notFound);

        assertThatCode(() -> service.handle(notification, "123456")).doesNotThrowAnyException();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}

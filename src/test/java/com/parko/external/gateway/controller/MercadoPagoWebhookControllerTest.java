package com.parko.external.gateway.controller;

import com.parko.external.gateway.security.MercadoPagoWebhookSignatureValidator;
import com.parko.external.gateway.service.PaymentNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MercadoPagoWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class MercadoPagoWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MercadoPagoWebhookSignatureValidator signatureValidator;

    @MockitoBean
    private PaymentNotificationService paymentNotificationService;

    private static final String NOTIFICATION_BODY = """
            {
              "action": "payment.updated",
              "type": "payment",
              "data": { "id": "123456" }
            }
            """;

    @Test
    void receiveNotification_returns200_andDelegatesToService_whenSignatureIsValid() throws Exception {
        when(signatureValidator.isValid(any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/webhooks/mercadopago")
                        .param("data.id", "123456")
                        .header("x-signature", "ts=1,v1=abc")
                        .header("x-request-id", "req-1")
                        .contentType("application/json")
                        .content(NOTIFICATION_BODY))
                .andExpect(status().isOk());

        verify(paymentNotificationService).handle(any(), eq("123456"));
    }

    @Test
    void receiveNotification_returns401_andSkipsService_whenSignatureIsInvalid() throws Exception {
        when(signatureValidator.isValid(any(), any(), any())).thenReturn(false);

        mockMvc.perform(post("/webhooks/mercadopago")
                        .param("data.id", "123456")
                        .header("x-signature", "ts=1,v1=abc")
                        .header("x-request-id", "req-1")
                        .contentType("application/json")
                        .content(NOTIFICATION_BODY))
                .andExpect(status().isUnauthorized());

        verify(paymentNotificationService, never()).handle(any(), anyString());
    }
}

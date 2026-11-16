package com.parko.external.gateway.controller;

import com.parko.external.gateway.dto.TicketPaymentRequest;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.service.MercadoPagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MercadoPagoService mercadoPagoService;

    @Test
    void create_returnsPreferenceFromService() throws Exception {
        TicketPaymentRequest request = new TicketPaymentRequest(UUID.randomUUID(), BigDecimal.valueOf(3000));
        when(mercadoPagoService.createTicketPaymentPreference(any(TicketPaymentRequest.class)))
                .thenReturn(new PreferenceResponse("pref-1", "https://mp.com/init", "https://mp.com/sandbox"));

        mockMvc.perform(post("/internal/ticket-payments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.init_point").value("https://mp.com/init"));
    }
}

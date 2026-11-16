package com.parko.external.gateway.controller;

import com.parko.external.gateway.dto.TicketPaymentRequest;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import com.parko.external.gateway.service.MercadoPagoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ticket-payments")
public class TicketPaymentController {

    private final MercadoPagoService mercadoPagoService;

    public TicketPaymentController(MercadoPagoService mercadoPagoService) {
        this.mercadoPagoService = mercadoPagoService;
    }

    @PostMapping
    public PreferenceResponse create(@RequestBody TicketPaymentRequest request) {
        return mercadoPagoService.createTicketPaymentPreference(request);
    }
}

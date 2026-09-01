package com.parko.external.gateway.client;

import com.parko.external.gateway.config.MercadoPagoFeignConfig;
import com.parko.external.gateway.dto.mercadopago.PaymentResponse;
import com.parko.external.gateway.dto.mercadopago.PreferenceRequest;
import com.parko.external.gateway.dto.mercadopago.PreferenceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "mercadopago",
        url = "${mercadopago.base-url}",
        configuration = MercadoPagoFeignConfig.class
)
public interface MercadoPagoClient {

    @PostMapping("/checkout/preferences")
    PreferenceResponse createPreference(@RequestBody PreferenceRequest request);

    @GetMapping("/v1/payments/{id}")
    PaymentResponse getPayment(@PathVariable("id") String id);
}

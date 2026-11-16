package com.parko.external.gateway.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class MercadoPagoFeignConfig {

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @Bean
    public RequestInterceptor mercadoPagoAuthInterceptor() {
        return template -> template.header("Authorization", "Bearer " + accessToken);
    }
}

package com.parko.external.gateway.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoFeignConfigTest {

    @Test
    void mercadoPagoAuthInterceptor_addsBearerAuthorizationHeader() {
        MercadoPagoFeignConfig config = new MercadoPagoFeignConfig();
        ReflectionTestUtils.setField(config, "accessToken", "TEST-token-123");

        RequestInterceptor interceptor = config.mercadoPagoAuthInterceptor();
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer TEST-token-123");
    }
}

package com.parko.external.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Valores de prueba: el servicio exige estas variables (no tienen valor por defecto) y ngrok no debe levantarse en tests.
@SpringBootTest(properties = {
		"mercadopago.access-token=test-access-token",
		"mercadopago.webhook-secret=test-webhook-secret",
		"ngrok.enabled=false"
})
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}

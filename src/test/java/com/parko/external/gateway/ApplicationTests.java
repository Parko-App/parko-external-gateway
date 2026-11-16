package com.parko.external.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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

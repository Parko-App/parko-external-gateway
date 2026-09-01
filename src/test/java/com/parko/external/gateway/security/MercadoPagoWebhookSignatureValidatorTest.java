package com.parko.external.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoWebhookSignatureValidatorTest {

    private static final String SECRET = "test-secret";

    private MercadoPagoWebhookSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MercadoPagoWebhookSignatureValidator();
        ReflectionTestUtils.setField(validator, "webhookSecret", SECRET);
    }

    @Test
    void isValid_returnsTrue_whenSignatureMatchesManifest() {
        String dataId = "123456";
        String xRequestId = "req-1";
        String ts = "1742505638683";
        String manifest = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        String hash = hmacSha256Hex(manifest, SECRET);
        String xSignature = "ts=" + ts + ",v1=" + hash;

        assertThat(validator.isValid(xSignature, xRequestId, dataId)).isTrue();
    }

    @Test
    void isValid_lowercasesDataIdBeforeBuildingManifest() {
        String dataId = "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3";
        String xRequestId = "req-1";
        String ts = "1742505638683";
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        String hash = hmacSha256Hex(manifest, SECRET);
        String xSignature = "ts=" + ts + ",v1=" + hash;

        assertThat(validator.isValid(xSignature, xRequestId, dataId)).isTrue();
    }

    @Test
    void isValid_returnsFalse_whenHashDoesNotMatch() {
        String xSignature = "ts=1742505638683,v1=deadbeef";

        assertThat(validator.isValid(xSignature, "req-1", "123456")).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenSignatureHeaderMissing() {
        assertThat(validator.isValid(null, "req-1", "123456")).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenSignatureHeaderMalformed() {
        assertThat(validator.isValid("not-a-valid-signature", "req-1", "123456")).isFalse();
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

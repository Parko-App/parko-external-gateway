package com.parko.external.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // --- H6: el secret no puede faltar, estar vacio ni ser el valor por defecto conocido ---

    private void withSecret(String secret) {
        ReflectionTestUtils.setField(validator, "webhookSecret", secret);
    }

    @Test
    void validateConfiguration_failsToStart_whenTheSecretIsMissingBlankOrThePlaceholder() {
        for (String bad : new String[]{null, "", "   ", "placeholder", "PLACEHOLDER", "  Placeholder  "}) {
            withSecret(bad);

            assertThatThrownBy(validator::validateConfiguration)
                    .as("secret = '%s'", bad)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MERCADOPAGO_WEBHOOK_SECRET");
        }
    }

    @Test
    void validateConfiguration_passes_withARealSecret() {
        withSecret("un-secret-real-de-mercado-pago");

        assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    void validateConfiguration_runsAtStartup() throws NoSuchMethodException {
        assertThat(MercadoPagoWebhookSignatureValidator.class.getDeclaredMethod("validateConfiguration")
                .isAnnotationPresent(jakarta.annotation.PostConstruct.class)).isTrue();
    }

    // --- ventana de frescura del ts (H8) ---

    private static final Instant NOW = Instant.parse("2026-11-15T20:00:00Z");

    private void enableFreshnessWindow(Duration maxAge) {
        ReflectionTestUtils.setField(validator, "maxAge", maxAge);
        ReflectionTestUtils.setField(validator, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private boolean validWithTs(String ts) {
        String manifest = "id:123456;request-id:req-1;ts:" + ts + ";";
        String xSignature = "ts=" + ts + ",v1=" + hmacSha256Hex(manifest, SECRET);
        return validator.isValid(xSignature, "req-1", "123456");
    }

    @Test
    void isValid_acceptsAFreshTs_inMilliseconds() {
        enableFreshnessWindow(Duration.ofHours(24));

        assertThat(validWithTs(String.valueOf(NOW.toEpochMilli()))).isTrue();
        assertThat(validWithTs(String.valueOf(NOW.minus(Duration.ofHours(23)).toEpochMilli()))).isTrue();
    }

    @Test
    void isValid_acceptsAFreshTs_inSeconds() {
        enableFreshnessWindow(Duration.ofHours(24));

        assertThat(validWithTs(String.valueOf(NOW.getEpochSecond()))).isTrue();
        assertThat(validWithTs(String.valueOf(NOW.minus(Duration.ofHours(25)).getEpochSecond()))).isFalse();
    }

    @Test
    void isValid_rejectsAReplayedWebhookWithAnOldTs_evenWithAValidSignature() {
        enableFreshnessWindow(Duration.ofHours(24));

        assertThat(validWithTs(String.valueOf(NOW.minus(Duration.ofHours(25)).toEpochMilli()))).isFalse();
        assertThat(validWithTs("1700000000000")).isFalse();
    }

    @Test
    void isValid_rejectsATsFromTheFuture_beyondTheAllowedClockSkew() {
        enableFreshnessWindow(Duration.ofHours(24));

        assertThat(validWithTs(String.valueOf(NOW.plus(Duration.ofMinutes(2)).toEpochMilli()))).isTrue();
        assertThat(validWithTs(String.valueOf(NOW.plus(Duration.ofMinutes(10)).toEpochMilli()))).isFalse();
    }

    @Test
    void isValid_rejectsANonNumericTs_whenTheWindowIsEnabled() {
        enableFreshnessWindow(Duration.ofHours(24));

        assertThat(validWithTs("no-es-un-numero")).isFalse();
    }

    @Test
    void isValid_doesNotCheckTheAge_whenTheWindowIsDisabled() {
        enableFreshnessWindow(Duration.ZERO);

        assertThat(validWithTs("1700000000000")).isTrue();
    }

    @Test
    void isValid_stillRejectsABadSignature_evenWithAFreshTs() {
        enableFreshnessWindow(Duration.ofHours(24));
        String ts = String.valueOf(NOW.toEpochMilli());

        assertThat(validator.isValid("ts=" + ts + ",v1=deadbeef", "req-1", "123456")).isFalse();
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

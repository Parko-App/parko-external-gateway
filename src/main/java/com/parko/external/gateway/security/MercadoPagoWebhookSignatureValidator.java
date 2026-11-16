package com.parko.external.gateway.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class MercadoPagoWebhookSignatureValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookSignatureValidator.class);
    private static final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final long SECONDS_MILLIS_THRESHOLD = 100_000_000_000L;

    private static final String PLACEHOLDER_SECRET = "placeholder";

    @Value("${mercadopago.webhook-secret}")
    private String webhookSecret;

    @PostConstruct
    void validateConfiguration() {
        if (webhookSecret == null || webhookSecret.isBlank() || PLACEHOLDER_SECRET.equalsIgnoreCase(webhookSecret.trim())) {
            throw new IllegalStateException("MERCADOPAGO_WEBHOOK_SECRET no esta configurado con un valor real "
                    + "(falta, esta vacio o es 'placeholder'). Definilo con el secret de firma de los webhooks de Mercado Pago.");
        }
    }

    @Value("${mercadopago.webhook-max-age:PT24H}")
    private Duration maxAge = Duration.ZERO;

    private Clock clock = Clock.systemUTC();

    public boolean isValid(String xSignature, String xRequestId, String dataId) {
        if (xSignature == null) {
            return false;
        }

        String ts = null;
        String receivedHash = null;
        for (String part : xSignature.split(",")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String key = keyValue[0].trim();
            String value = keyValue[1].trim();
            if ("ts".equals(key)) {
                ts = value;
            } else if ("v1".equals(key)) {
                receivedHash = value;
            }
        }
        if (ts == null || receivedHash == null) {
            return false;
        }

        String manifest = buildManifest(dataId, xRequestId, ts);
        String expectedHash = hmacSha256Hex(manifest, webhookSecret);

        boolean signatureMatches = MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                receivedHash.getBytes(StandardCharsets.UTF_8)
        );
        return signatureMatches && isFresh(ts);
    }

    private boolean isFresh(String ts) {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            return true;
        }
        long tsMillis;
        try {
            long raw = Long.parseLong(ts);
            tsMillis = raw < SECONDS_MILLIS_THRESHOLD ? raw * 1000 : raw;
        } catch (NumberFormatException e) {
            log.warn("Webhook de Mercado Pago con ts no numerico, se rechaza");
            return false;
        }
        long ageMillis = clock.millis() - tsMillis;
        boolean fresh = ageMillis <= maxAge.toMillis() && ageMillis >= -ALLOWED_CLOCK_SKEW.toMillis();
        if (!fresh) {
            log.warn("Webhook de Mercado Pago con ts fuera de la ventana permitida (antiguedad={} ms, maximo={})",
                    ageMillis, maxAge);
        }
        return fresh;
    }

    private String buildManifest(String dataId, String xRequestId, String ts) {
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:").append(dataId.toLowerCase()).append(";");
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(";");
        }
        manifest.append("ts:").append(ts).append(";");
        return manifest.toString();
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC del webhook de Mercado Pago", e);
        }
    }
}

package com.parko.external.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class MercadoPagoWebhookSignatureValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${mercadopago.webhook-secret}")
    private String webhookSecret;

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

        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                receivedHash.getBytes(StandardCharsets.UTF_8)
        );
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

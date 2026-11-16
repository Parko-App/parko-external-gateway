package com.parko.external.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Recuerda que un pago de Mercado Pago ya fue procesado para no publicar dos veces el mismo evento.
 * Mercado Pago reenvia webhooks (at-least-once) y un webhook firmado se puede reenviar a mano: sin esto,
 * cada reenvio generaba un nuevo evento de credito.
 * <p>
 * La clave es (id del pago + estado), asi un mismo pago puede pasar por estados distintos pero cada estado
 * terminal se procesa una sola vez.
 */
@Component
public class PaymentDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(PaymentDeduplicator.class);
    private static final String KEY_PREFIX = "gateway:mp:processed:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public PaymentDeduplicator(StringRedisTemplate redisTemplate,
                               @Value("${mercadopago.webhook-dedup-ttl:P7D}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    /**
     * Reclama el procesamiento del pago de forma atomica. Devuelve true solo para quien lo reclama primero.
     * Si Redis no esta disponible la excepcion se propaga a proposito (fail-closed): el webhook responde 5xx y
     * Mercado Pago lo reintenta mas tarde, en vez de arriesgar una doble acreditacion.
     */
    public boolean markIfFirst(String paymentId, String status) {
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key(paymentId, status), "1", ttl);
        return Boolean.TRUE.equals(first);
    }

    /** Libera la reclamacion si el procesamiento fallo, para que el reintento de Mercado Pago pueda volver a intentarlo. */
    public void release(String paymentId, String status) {
        try {
            redisTemplate.delete(key(paymentId, status));
        } catch (RuntimeException e) {
            log.warn("No se pudo liberar la marca de procesamiento del pago {} (status={})", paymentId, status, e);
        }
    }

    private String key(String paymentId, String status) {
        return KEY_PREFIX + paymentId + ":" + status;
    }
}

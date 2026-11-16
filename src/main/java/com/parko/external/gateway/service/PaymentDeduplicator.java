package com.parko.external.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

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

    public boolean markIfFirst(String paymentId, String status) {
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key(paymentId, status), "1", ttl);
        return Boolean.TRUE.equals(first);
    }

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

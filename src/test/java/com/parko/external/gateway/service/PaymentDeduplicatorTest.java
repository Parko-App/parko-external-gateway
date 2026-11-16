package com.parko.external.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentDeduplicatorTest {

    private static final Duration TTL = Duration.ofDays(7);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PaymentDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new PaymentDeduplicator(redisTemplate, TTL);
    }

    @Test
    void markIfFirst_returnsTrue_whenTheKeyWasNotThere() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("gateway:mp:processed:999:approved", "1", TTL)).thenReturn(true);

        assertThat(deduplicator.markIfFirst("999", "approved")).isTrue();
    }

    @Test
    void markIfFirst_returnsFalse_whenTheKeyWasAlreadyThere() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("gateway:mp:processed:999:approved", "1", TTL)).thenReturn(false);

        assertThat(deduplicator.markIfFirst("999", "approved")).isFalse();
    }

    @Test
    void markIfFirst_returnsFalse_whenRedisAnswersNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("gateway:mp:processed:999:approved", "1", TTL)).thenReturn(null);

        assertThat(deduplicator.markIfFirst("999", "approved")).isFalse();
    }

    @Test
    void markIfFirst_propagatesTheError_whenRedisIsDown_soTheWebhookIsRetried() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("gateway:mp:processed:999:approved", "1", TTL))
                .thenThrow(new RedisConnectionFailureException("redis caido"));

        assertThatThrownBy(() -> deduplicator.markIfFirst("999", "approved"))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void keyIncludesTheStatus_soDifferentTerminalStatesAreIndependent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("gateway:mp:processed:999:rejected", "1", TTL)).thenReturn(true);

        assertThat(deduplicator.markIfFirst("999", "rejected")).isTrue();
        verify(valueOperations).setIfAbsent("gateway:mp:processed:999:rejected", "1", TTL);
    }

    @Test
    void release_deletesTheKey() {
        deduplicator.release("999", "approved");

        verify(redisTemplate).delete("gateway:mp:processed:999:approved");
    }

    @Test
    void release_doesNotThrow_whenRedisFails() {
        doThrow(new RedisConnectionFailureException("redis caido")).when(redisTemplate)
                .delete("gateway:mp:processed:999:approved");

        assertThatCode(() -> deduplicator.release("999", "approved")).doesNotThrowAnyException();
    }
}

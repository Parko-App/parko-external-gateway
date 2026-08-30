package com.parko.external.gateway.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String BALANCE_EXCHANGE = "balance.exchange";

    public static final String TOPUP_QUEUE = "balance.topup.queue";
    public static final String TOPUP_ROUTING_KEY = "balance.topup";

    @Bean
    public DirectExchange balanceExchange() {
        return new DirectExchange(BALANCE_EXCHANGE);
    }

    @Bean
    public Queue topUpQueue() {
        return new Queue(TOPUP_QUEUE, true);
    }

    @Bean
    public Binding topUpBinding(Queue topUpQueue, DirectExchange balanceExchange) {
        return BindingBuilder.bind(topUpQueue).to(balanceExchange).with(TOPUP_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        // INFERRED: ignora el __TypeId__ del productor (apunta a la clase de balance-service, no existe acá)
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}

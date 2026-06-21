package org.side_project.audit_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;
    @Value("${rabbitmq.queue}")
    private String queue;
    @Value("${rabbitmq.routing-key}")
    private String routingKey;
    @Value("${rabbitmq.dead-letter-exchange}")
    private String dlx;
    @Value("${rabbitmq.dead-letter-queue}")
    private String dlq;
    @Value("${rabbitmq.dead-letter-routing-key}")
    private String dlqRoutingKey;

    // ── Main queue (declaration must match wallet_system's publisher-side declaration) ──
    @Bean
    public DirectExchange auditExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(auditExchange()).with(routingKey);
    }

    // ── Dead-letter queue ──
    @Bean
    public DirectExchange auditDeadLetterExchange() {
        return new DirectExchange(dlx);
    }

    @Bean
    public Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(dlq).build();
    }

    @Bean
    public Binding auditDeadLetterBinding() {
        return BindingBuilder.bind(auditDeadLetterQueue()).to(auditDeadLetterExchange()).with(dlqRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Spring Boot 4 auto-configures a Jackson 3 ObjectMapper, but Jackson2JsonMessageConverter
        // needs a Jackson 2 (com.fasterxml.jackson) ObjectMapper — so build our own here.
        // JavaTimeModule lets us (de)serialize the java.time fields on AuditLog.
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        // The publisher (wallet_system) stamps a __TypeId__ header with its own class name.
        // INFERRED precedence makes us deserialize into the @RabbitListener parameter type
        // (our AuditLog) instead of trying to load the publisher's class.
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}

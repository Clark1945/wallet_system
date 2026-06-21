package org.side_project.wallet_system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class RabbitMQConfig {

    @Value("${rabbitmq.email.exchange}")
    private String exchange;

    @Value("${rabbitmq.email.queue}")
    private String queue;

    @Value("${rabbitmq.email.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.email.dead-letter-exchange}")
    private String dlx;

    @Value("${rabbitmq.email.dead-letter-routing-key}")
    private String dlqRoutingKey;

    // ── Audit log events (wallet.audit.log) ───────────────────────────────
    @Value("${rabbitmq.auditLog.exchange}")
    private String auditExchangeName;

    @Value("${rabbitmq.auditLog.queue}")
    private String auditQueueName;

    @Value("${rabbitmq.auditLog.routing-key}")
    private String auditRoutingKey;

    @Value("${rabbitmq.auditLog.dead-letter-exchange}")
    private String auditDlxName;

    @Value("${rabbitmq.auditLog.dead-letter-routing-key}")
    private String auditDlqRoutingKey;

    // Declare the queue with DLX args so both publisher and consumer agree on the
    // queue definition. Prevents RabbitMQ PRECONDITION_FAILED on redeclaration.
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public DirectExchange emailExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(emailExchange()).with(routingKey);
    }

    // ── Audit topology ────────────────────────────────────────────────────
    @Bean
    public DirectExchange auditExchange() {
        return new DirectExchange(auditExchangeName);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(auditQueueName)
                .withArgument("x-dead-letter-exchange", auditDlxName)
                .withArgument("x-dead-letter-routing-key", auditDlqRoutingKey)
                .build();
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(auditExchange()).with(auditRoutingKey);
    }

    // Audit dead-letter topology. Unlike email (whose DLQ is owned by email-service),
    // nothing else declares the audit DLQ yet, so declare it here to avoid silently
    // dropping dead-lettered audit events.
    @Bean
    public DirectExchange auditDlxExchange() {
        return new DirectExchange(auditDlxName);
    }

    @Bean
    public Queue auditDlq() {
        return QueueBuilder.durable(auditQueueName + ".dlq").build();
    }

    @Bean
    public Binding auditDlqBinding() {
        return BindingBuilder.bind(auditDlq()).to(auditDlxExchange()).with(auditDlqRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Register JavaTimeModule so payloads with java.time types (e.g. AuditLog.createdAt)
        // serialize as ISO-8601 instead of failing.
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}

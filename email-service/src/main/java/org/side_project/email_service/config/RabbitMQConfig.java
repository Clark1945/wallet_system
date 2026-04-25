package org.side_project.email_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

    // ── Main queue ──
    // x-dead-letter-exchange routes messages here after Spring Boot AMQP
    // exhausts all retry attempts (configured via application.yaml).

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
    public Binding emailBinding(Queue emailQueue, DirectExchange emailExchange) {
        return BindingBuilder.bind(emailQueue).to(emailExchange).with(routingKey);
    }

    // ── Dead Letter Queue ──

    @Bean
    public DirectExchange emailDeadLetterExchange() {
        return new DirectExchange(dlx);
    }

    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(dlq).build();
    }

    @Bean
    public Binding emailDeadLetterBinding(Queue emailDeadLetterQueue,
                                          DirectExchange emailDeadLetterExchange) {
        return BindingBuilder.bind(emailDeadLetterQueue).to(emailDeadLetterExchange).with(dlqRoutingKey);
    }

    // Spring Boot autoconfiguration detects this bean and injects it into both
    // RabbitTemplate and SimpleRabbitListenerContainerFactory automatically.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

package org.side_project.email_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

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

    // ── Main queue (routes failed messages to DLX after retry exhaustion) ──

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

    // ── Message converter ──

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ── Listener container factory with retry interceptor ──

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {

        // Retry 3 times with exponential backoff (2 s → 4 s → 8 s).
        // After exhaustion, RejectAndDontRequeueRecoverer NACKs the message
        // so RabbitMQ routes it to the DLQ via x-dead-letter-exchange.
        RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2_000, 2.0, 10_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}

package com.Leon.accommodation_finder.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring creates these in RabbitMQ when the app first connects, if they do not exist yet.
@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.audit-queue}")
    private String auditQueueName;

    @Value("${app.rabbitmq.notification-queue}")
    private String notificationQueueName;

    // a topic exchange sorts each event by its routing key, for example booking.created
    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(exchangeName);
    }

    // durable = the queue survives a RabbitMQ restart
    @Bean
    public Queue auditQueue() {
        return new Queue(auditQueueName, true);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(notificationQueueName, true);
    }

    // # matches every routing key, so the audit queue gets a copy of every event
    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(eventsExchange()).with("#");
    }

    // the notification queue only gets the events that send someone an email
    @Bean
    public Binding bookingCreatedBinding() {
        return BindingBuilder.bind(notificationQueue()).to(eventsExchange()).with("booking.created");
    }

    @Bean
    public Binding bookingConfirmedBinding() {
        return BindingBuilder.bind(notificationQueue()).to(eventsExchange()).with("booking.confirmed");
    }

    // events travel as JSON instead of Java's own binary format, so they are readable on the RabbitMQ page
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}

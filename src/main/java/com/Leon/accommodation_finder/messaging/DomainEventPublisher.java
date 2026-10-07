package com.Leon.accommodation_finder.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Services never talk to RabbitMQ. They call applicationEventPublisher.publishEvent(new DomainEvent(...)),
// and Spring hands the event to this listener, which forwards it to RabbitMQ.
@Component
public class DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Autowired
    public DomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // AFTER_COMMIT: inside a transaction, the event waits until the database has really saved
    // the change, and is dropped if the transaction rolls back.
    // fallbackExecution = true: when there is no transaction at all (for example the login steps),
    // the event is sent straight away instead of being silently ignored.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(DomainEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, event.getEventType(), event);
        } catch (Exception e) {
            // a RabbitMQ problem must never fail a login, booking or payment, so only log it
            logger.error("Could not send event {} ({}) to RabbitMQ: {}",
                    event.getEventType(), event.getEventId(), e.getMessage());
        }
    }
}

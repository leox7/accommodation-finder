package com.Leon.accommodation_finder.messaging;

import com.Leon.accommodation_finder.model.AuditLog;
import com.Leon.accommodation_finder.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Reads every event from the audit queue and saves it as one AuditLog row.
// This is the only place in the app that writes AuditLog rows.
@Component
public class AuditConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AuditConsumer.class);

    private final AuditLogRepository auditLogRepository;

    @Autowired
    public AuditConsumer(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @RabbitListener(queues = "${app.rabbitmq.audit-queue}")
    public void receive(DomainEvent event) {
        // errors are logged and not rethrown: a rethrown error would make RabbitMQ
        // deliver the same message again and again
        try {
            // RabbitMQ may deliver the same message twice, the first copy is enough
            if (auditLogRepository.existsByEventId(event.getEventId())) {
                logger.info("Event {} is already audited, skipping", event.getEventId());
                return;
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setEventId(event.getEventId());
            auditLog.setTimestamp(event.getTimestamp());
            auditLog.setActorEmail(event.getActorEmail());
            auditLog.setActorRole(event.getActorRole());
            auditLog.setAction(event.getEventType());
            auditLog.setEntityType(event.getEntityType());
            auditLog.setEntityId(event.getEntityId());
            auditLog.setDetails(event.getDetails());
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            logger.error("Error while saving audit row for event {}: {}", event.getEventId(), e.getMessage());
        }
    }
}

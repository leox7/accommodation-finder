package com.Leon.accommodation_finder.messaging;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// A small message saying "something happened", for example a booking was created.
// Never put sensitive data in here: no passwords, OTPs, tokens, phone numbers,
// national IDs or recipient emails. Consumers look those up in the database themselves.
@Getter
@Setter
@NoArgsConstructor
public class DomainEvent {

    // a random id, so a consumer can spot the same message arriving twice
    private String eventId;
    // also used as the RabbitMQ routing key, for example booking.created
    private String eventType;
    // the logged in user's email, or SYSTEM when the app did it by itself
    private String actorEmail;
    private String actorRole;
    private String entityType;
    private Long entityId;
    private LocalDateTime timestamp;
    private String details;

    public DomainEvent(String eventType, String actorEmail, String actorRole,
                       String entityType, Long entityId, String details) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.entityType = entityType;
        this.entityId = entityId;
        this.timestamp = LocalDateTime.now();
        this.details = details;
    }
}

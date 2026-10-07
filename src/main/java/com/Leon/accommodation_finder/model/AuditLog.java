package com.Leon.accommodation_finder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One row per domain event. Written only by AuditConsumer, never updated or deleted.
// Never store passwords, OTPs, tokens, national IDs or phone numbers here.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique, so the same event can never be saved twice
    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private String actorEmail;

    private String actorRole;

    // the event type, for example booking.created
    @Column(nullable = false)
    private String action;

    private String entityType;

    private Long entityId;

    private String details;
}

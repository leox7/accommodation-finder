package com.Leon.accommodation_finder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// A message for a landlord, for example "new booking on your listing".
// Written only by NotificationConsumer.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "landlord_id", nullable = false)
    private Landlord landlord;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    // READ is a reserved word in MySQL, so the column gets a different name
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    // the event that caused this notification, unique so a repeated message is not saved twice
    @Column(nullable = false, unique = true)
    private String eventId;

    private LocalDateTime dateCreated;
}

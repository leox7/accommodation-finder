package com.Leon.accommodation_finder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// A message sent from the public contact form by someone who may not have an account.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InquirySubject subject;

    @Column(nullable = false, length = 500)
    private String message;

    // true once the support email has been sent, so a repeated RabbitMQ message does not send it twice
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean supportNotified = false;

    private LocalDateTime dateCreated;
}

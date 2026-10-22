package com.Leon.accommodation_finder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// A star rating (1 to 5) and an optional comment that a student leaves on a listing after staying there.
// One review per student per listing, the unique constraint is the safety net for that rule.
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "listing_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    private LocalDateTime dateCreated;
}

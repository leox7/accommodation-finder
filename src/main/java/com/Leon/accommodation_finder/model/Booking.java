package com.Leon.accommodation_finder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// A student's request to stay in a listing from startDate to endDate.
// endDate is the last day of the stay, so another stay may start the day after.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Booking {

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
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    private LocalDateTime dateCreated;

    // the day the next month's rent is due. Empty until the deposit is paid, then set to startDate,
    // and moved forward one month by each paid rent. Once it is after endDate there is nothing more to pay
    private LocalDate nextDueDate;

    // the due date the landlord was last reminded about, so the daily job never reminds twice
    // for the same due date. Empty until the first rent due reminder
    private LocalDate lastRemindedDueDate;
}

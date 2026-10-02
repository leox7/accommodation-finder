package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.Booking;
import com.Leon.accommodation_finder.model.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Two stays overlap when existingStart <= newEnd AND existingEnd >= newStart.
    // Only bookings whose status is in the given list are counted.
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.listing.id = :listingId " +
            "AND b.status IN :statuses " +
            "AND b.startDate <= :endDate AND b.endDate >= :startDate")
    long countListingOverlaps(@Param("listingId") Long listingId,
                              @Param("statuses") List<BookingStatus> statuses,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    // same overlap rule, but for one student's bookings on any listing
    @Query("SELECT COUNT(b) FROM Booking b WHERE LOWER(b.student.email) = LOWER(:email) " +
            "AND b.status IN :statuses " +
            "AND b.startDate <= :endDate AND b.endDate >= :startDate")
    long countStudentOverlaps(@Param("email") String email,
                              @Param("statuses") List<BookingStatus> statuses,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    // used before deleting a listing, true if any booking on it is in one of the given statuses
    boolean existsByListingIdAndStatusIn(Long listingId, List<BookingStatus> statuses);

    // removes the remaining (cancelled or vacated) bookings when their listing is deleted
    void deleteByListingId(Long listingId);

    @Query("SELECT b FROM Booking b WHERE LOWER(b.student.email) = LOWER(:email)")
    Page<Booking> findMyBookings(@Param("email") String email, Pageable pageable);

    // bookings on every listing owned by the given landlord
    @Query("SELECT b FROM Booking b WHERE LOWER(b.listing.landlord.email) = LOWER(:email)")
    Page<Booking> findLandlordBookings(@Param("email") String email, Pageable pageable);

    // used when a booking is confirmed: every other PENDING request on the same listing
    // whose dates overlap the confirmed stay is cancelled in one statement.
    // Must be called inside a transaction (the calling service method is @Transactional).
    @Modifying
    @Query("UPDATE Booking b SET b.status = com.Leon.accommodation_finder.model.BookingStatus.CANCELLED " +
            "WHERE b.listing.id = :listingId " +
            "AND b.status = com.Leon.accommodation_finder.model.BookingStatus.PENDING " +
            "AND b.id <> :bookingId " +
            "AND b.startDate <= :endDate AND b.endDate >= :startDate")
    int cancelOverlappingPending(@Param("listingId") Long listingId,
                                 @Param("bookingId") Long bookingId,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);

    // used after vacating: true if the listing still has another booking with the given status
    boolean existsByListingIdAndStatusAndIdNot(Long listingId, BookingStatus status, Long id);
}

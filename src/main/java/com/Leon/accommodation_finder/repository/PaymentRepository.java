package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.Payment;
import com.Leon.accommodation_finder.model.PaymentProviderType;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.model.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // true if the booking already has a payment of this type in this status, for example a PAID DEPOSIT
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Payment p " +
            "WHERE p.booking.id = :bookingId AND p.type = :type AND p.status = :status")
    boolean existsForBooking(@Param("bookingId") Long bookingId,
                             @Param("type") PaymentType type,
                             @Param("status") PaymentStatus status);

    // true if this phone number has a payment in this status, on any booking
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Payment p " +
            "WHERE p.phoneNumber = :phoneNumber AND p.status = :status")
    boolean existsForPhoneNumber(@Param("phoneNumber") String phoneNumber,
                                 @Param("status") PaymentStatus status);

    // true if any booking on this listing has a payment, in any status. Used before deleting a listing
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Payment p " +
            "WHERE p.booking.listing.id = :listingId")
    boolean existsForListing(@Param("listingId") Long listingId);

    // the M-Pesa callback and the status checks find the payment by the id Safaricom gave it
    Optional<Payment> findByCheckoutRequestId(String checkoutRequestId);

    // payments the STK Push Query fallback may still ask about, for example PENDING, MPESA, fewer than 3 checks
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.provider = :provider " +
            "AND p.statusChecks < :maxChecks")
    List<Payment> findForStatusCheck(@Param("status") PaymentStatus status,
                                     @Param("provider") PaymentProviderType provider,
                                     @Param("maxChecks") int maxChecks);

    // Adds 1 to statusChecks and changes nothing else on the row. A plain save() of an older copy
    // of the payment could put back an old status (for example PENDING over a callback's PAID).
    // @Transactional because an UPDATE query needs a transaction and the caller has none.
    @Transactional
    @Modifying
    @Query("UPDATE Payment p SET p.statusChecks = p.statusChecks + 1 WHERE p.id = :id")
    void addStatusCheck(@Param("id") Long id);

    @Query("SELECT p FROM Payment p WHERE LOWER(p.booking.student.email) = LOWER(:email)")
    Page<Payment> findStudentPayments(@Param("email") String email, Pageable pageable);

    // payments on every listing owned by the given landlord
    @Query("SELECT p FROM Payment p WHERE LOWER(p.booking.listing.landlord.email) = LOWER(:email)")
    Page<Payment> findLandlordPayments(@Param("email") String email, Pageable pageable);
}

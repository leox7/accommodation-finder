package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.Payment;
import com.Leon.accommodation_finder.model.PaymentProviderType;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.model.PaymentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    // Reads the payment AND locks its row until the current transaction ends (SELECT ... FOR UPDATE).
    // If the callback and the status check job try to change the same payment at the same moment,
    // the second one waits here, then sees the new status and does nothing. Only call it inside @Transactional.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    // payments the STK Push Query fallback may still ask about, for example all PENDING MPESA payments
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.provider = :provider")
    List<Payment> findForStatusCheck(@Param("status") PaymentStatus status,
                                     @Param("provider") PaymentProviderType provider);

    // Adds 1 to statusChecks, records when the check ran and changes nothing else on the row. A plain save()
    // of an older copy of the payment could put back an old status (for example PENDING over a callback's PAID).
    // @Transactional because an UPDATE query needs a transaction and the caller has none.
    @Transactional
    @Modifying
    @Query("UPDATE Payment p SET p.statusChecks = p.statusChecks + 1, p.lastCheckedAt = :checkedAt WHERE p.id = :id")
    void addStatusCheck(@Param("id") Long id, @Param("checkedAt") LocalDateTime checkedAt);

    // Saves the ids Safaricom gave an accepted STK Push and changes nothing else, for the same reason as above:
    // a callback may already have changed the status, and saving the older copy would undo that.
    @Transactional
    @Modifying
    @Query("UPDATE Payment p SET p.merchantRequestId = :merchantRequestId, " +
            "p.checkoutRequestId = :checkoutRequestId WHERE p.id = :id")
    void saveCheckoutIds(@Param("id") Long id,
                         @Param("merchantRequestId") String merchantRequestId,
                         @Param("checkoutRequestId") String checkoutRequestId);

    // Orphan callback: Safaricom says "paid" for a CheckoutRequestID we never saved (the app stopped while
    // calling Safaricom). The payment it belongs to is an MPESA payment with no checkoutRequestId, still
    // PENDING or already FAILED, from the same phone, created recently. The STK amount was the price rounded
    // UP to whole shillings, so the stored amount is above (callback amount - 1) and at most the callback amount.
    @Query("SELECT p FROM Payment p WHERE p.provider = :provider AND p.checkoutRequestId IS NULL " +
            "AND p.status IN :statuses AND p.phoneNumber = :phoneNumber " +
            "AND p.amount > :amountMinusOne AND p.amount <= :amount AND p.dateCreated >= :since")
    List<Payment> findOrphanCandidates(@Param("provider") PaymentProviderType provider,
                                       @Param("statuses") List<PaymentStatus> statuses,
                                       @Param("phoneNumber") String phoneNumber,
                                       @Param("amountMinusOne") BigDecimal amountMinusOne,
                                       @Param("amount") BigDecimal amount,
                                       @Param("since") LocalDateTime since);

    // every payment, for admins. A null status means "all statuses"
    @Query("SELECT p FROM Payment p WHERE (:status IS NULL OR p.status = :status)")
    Page<Payment> findAllPayments(@Param("status") PaymentStatus status, Pageable pageable);

    // ---- report queries: payments created from :from (included) to :to (not included).
    // A null status means "all statuses". A STATUS row is [status, count, sum of amounts],
    // a MONTH row is [year, month, count, sum of amounts].
    // The landlord versions only see payments on that landlord's own listings. ----

    @Query("SELECT p.status, COUNT(p), SUM(p.amount) FROM Payment p " +
            "WHERE p.booking.listing.landlord.email = :email " +
            "AND p.dateCreated >= :from AND p.dateCreated < :to " +
            "AND (:status IS NULL OR p.status = :status) " +
            "GROUP BY p.status ORDER BY p.status")
    List<Object[]> sumLandlordPaymentsByStatus(@Param("email") String email,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               @Param("status") PaymentStatus status);

    @Query("SELECT YEAR(p.dateCreated), MONTH(p.dateCreated), COUNT(p), SUM(p.amount) FROM Payment p " +
            "WHERE p.booking.listing.landlord.email = :email " +
            "AND p.dateCreated >= :from AND p.dateCreated < :to " +
            "AND (:status IS NULL OR p.status = :status) " +
            "GROUP BY YEAR(p.dateCreated), MONTH(p.dateCreated) " +
            "ORDER BY YEAR(p.dateCreated), MONTH(p.dateCreated)")
    List<Object[]> sumLandlordPaymentsByMonth(@Param("email") String email,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to,
                                              @Param("status") PaymentStatus status);

    @Query("SELECT p.status, COUNT(p), SUM(p.amount) FROM Payment p " +
            "WHERE p.dateCreated >= :from AND p.dateCreated < :to " +
            "AND (:status IS NULL OR p.status = :status) " +
            "GROUP BY p.status ORDER BY p.status")
    List<Object[]> sumAllPaymentsByStatus(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to,
                                          @Param("status") PaymentStatus status);

    @Query("SELECT YEAR(p.dateCreated), MONTH(p.dateCreated), COUNT(p), SUM(p.amount) FROM Payment p " +
            "WHERE p.dateCreated >= :from AND p.dateCreated < :to " +
            "AND (:status IS NULL OR p.status = :status) " +
            "GROUP BY YEAR(p.dateCreated), MONTH(p.dateCreated) " +
            "ORDER BY YEAR(p.dateCreated), MONTH(p.dateCreated)")
    List<Object[]> sumAllPaymentsByMonth(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         @Param("status") PaymentStatus status);

    // income = PAID payments only. SUM gives null when there are no rows, the service turns that into 0
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.booking.listing.landlord.email = :email " +
            "AND p.status = com.Leon.accommodation_finder.model.PaymentStatus.PAID " +
            "AND p.dateCreated >= :from AND p.dateCreated < :to")
    BigDecimal sumLandlordIncome(@Param("email") String email,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    @Query("SELECT SUM(p.amount) FROM Payment p " +
            "WHERE p.status = com.Leon.accommodation_finder.model.PaymentStatus.PAID " +
            "AND p.dateCreated >= :from AND p.dateCreated < :to")
    BigDecimal sumAllIncome(@Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    @Query("SELECT p FROM Payment p WHERE LOWER(p.booking.student.email) = LOWER(:email)")
    Page<Payment> findStudentPayments(@Param("email") String email, Pageable pageable);

    // payments on every listing owned by the given landlord
    @Query("SELECT p FROM Payment p WHERE LOWER(p.booking.listing.landlord.email) = LOWER(:email)")
    Page<Payment> findLandlordPayments(@Param("email") String email, Pageable pageable);
}

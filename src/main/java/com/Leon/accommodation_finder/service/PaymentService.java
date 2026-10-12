package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.MpesaCallbackDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.dto.PaymentRequestDto;
import com.Leon.accommodation_finder.dto.PaymentResponseDto;
import com.Leon.accommodation_finder.exception.BookingNotFoundException;
import com.Leon.accommodation_finder.exception.InvalidPhoneNumberException;
import com.Leon.accommodation_finder.exception.PaymentConflictException;
import com.Leon.accommodation_finder.exception.PaymentNotFoundException;
import com.Leon.accommodation_finder.exception.PaymentOwnershipException;
import com.Leon.accommodation_finder.exception.PaymentProviderException;
import com.Leon.accommodation_finder.messaging.DomainEvent;
import com.Leon.accommodation_finder.model.Booking;
import com.Leon.accommodation_finder.model.BookingStatus;
import com.Leon.accommodation_finder.model.Payment;
import com.Leon.accommodation_finder.model.PaymentProviderType;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.model.PaymentType;
import com.Leon.accommodation_finder.provider.PaymentProvider;
import com.Leon.accommodation_finder.repository.BookingRepository;
import com.Leon.accommodation_finder.repository.PaymentRepository;
import com.Leon.accommodation_finder.util.MaskingUtil;
import com.Leon.accommodation_finder.util.PhoneUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Never log or put in an event the student's phone number, only payment and booking ids.
@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private static final int MAX_PAGE_SIZE = 50;

    // the actor in events when the app itself finished a payment (M-Pesa callback or status check)
    public static final String SYSTEM = "SYSTEM";

    // an orphan callback is only matched to a payment created this many minutes ago or less
    private static final int ORPHAN_MATCH_MINUTES = 30;

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentProvider paymentProvider;
    private final ApplicationEventPublisher eventPublisher;
    // This same service, but reached through Spring. Calling this.markPaid() directly would skip
    // @Transactional (Spring only adds the transaction when the call comes from outside the class),
    // so createPayment calls self.markPaid() instead. @Lazy lets Spring hand it over before it is fully built.
    private final PaymentService self;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, BookingRepository bookingRepository,
                          PaymentProvider paymentProvider, ApplicationEventPublisher eventPublisher,
                          @Lazy PaymentService self) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.paymentProvider = paymentProvider;
        this.eventPublisher = eventPublisher;
        this.self = self;
    }

    // studentEmail is the currently logged in student, taken from their JWT, never from the request body.
    // Keep this method WITHOUT @Transactional: the PENDING payment and the payment.initiated event must
    // exist even if the provider step fails, and with no transaction the event is sent straight away.
    public PaymentResponseDto createPayment(PaymentRequestDto request, String studentEmail) {
        try {
            // first of all, so every rule below uses the same 254XXXXXXXXX form
            String phoneNumber = PhoneUtil.normalize(request.getPhoneNumber());

            Booking booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new BookingNotFoundException(request.getBookingId()));

            if (!booking.getStudent().getEmail().equalsIgnoreCase(studentEmail)) {
                throw new PaymentOwnershipException("You can only pay for your own bookings");
            }

            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                throw new PaymentConflictException("Only a confirmed booking can be paid for");
            }

            // Listing.price is a Double, it is turned into BigDecimal here and never used as a double again
            BigDecimal amount = BigDecimal.valueOf(booking.getListing().getPrice());

            // the earlier payment may still turn out PAID, a new one now could charge the student twice
            if (paymentRepository.existsForBooking(booking.getId(), request.getType(), PaymentStatus.UNKNOWN)) {
                throw new PaymentConflictException("Your previous payment is still being confirmed");
            }

            if (request.getType() == PaymentType.DEPOSIT) {
                if (paymentRepository.existsForBooking(booking.getId(), PaymentType.DEPOSIT, PaymentStatus.PAID)) {
                    throw new PaymentConflictException("The deposit for this booking is already paid");
                }
                if (paymentRepository.existsForBooking(booking.getId(), PaymentType.DEPOSIT, PaymentStatus.PENDING)) {
                    throw new PaymentConflictException("A deposit payment for this booking is still being processed");
                }
            } else {
                if (!paymentRepository.existsForBooking(booking.getId(), PaymentType.DEPOSIT, PaymentStatus.PAID)) {
                    throw new PaymentConflictException("The deposit must be paid before any rent");
                }
                if (paymentRepository.existsForBooking(booking.getId(), PaymentType.RENT, PaymentStatus.PENDING)) {
                    throw new PaymentConflictException("A rent payment for this booking is still being processed");
                }
                if (booking.getNextDueDate().isAfter(booking.getEndDate())) {
                    throw new PaymentConflictException("All rent for this booking is paid, there is nothing more to pay");
                }
            }

            // M-Pesa allows only one open request per phone, so the same rule applies to every provider.
            // An UNKNOWN payment counts as open, it is still being confirmed
            if (paymentRepository.existsForPhoneNumber(phoneNumber, PaymentStatus.PENDING)
                    || paymentRepository.existsForPhoneNumber(phoneNumber, PaymentStatus.UNKNOWN)) {
                throw new PaymentConflictException("This phone number already has a payment being processed, wait for it to finish");
            }

            Payment payment = new Payment();
            payment.setBooking(booking);
            payment.setType(request.getType());
            payment.setAmount(amount);
            // status, provider and dateCreated are system controlled, a new payment always starts as pending
            payment.setStatus(PaymentStatus.PENDING);
            payment.setProvider(paymentProvider.getType());
            payment.setPhoneNumber(phoneNumber);
            payment.setDateCreated(LocalDateTime.now());
            // The PENDING row is saved BEFORE the provider is called, and not inside one big transaction.
            // If the app crashed after Safaricom charged the student, the row would otherwise not exist
            // and the money could never be matched to a booking. With the row saved first, every request
            // sent to Safaricom has a payment behind it, and the status checks can clean up anything left PENDING.
            // A transaction would also keep a database connection busy while waiting up to 30s for Safaricom.
            Payment saved = paymentRepository.save(payment);

            eventPublisher.publishEvent(new DomainEvent("payment.initiated", studentEmail, "STUDENT",
                    "Payment", saved.getId(), paymentDetails(saved)));

            PaymentStatus result;
            try {
                result = paymentProvider.startPayment(saved);
            } catch (PaymentProviderException e) {
                // the request never reached the student's phone, so this payment can never finish.
                // It is closed as FAILED, which does not block a new attempt, and the client gets 502
                self.markFailed(saved.getId(), "provider did not accept the request", studentEmail, "STUDENT");
                throw e;
            }

            if (result == PaymentStatus.PAID) {
                self.markPaid(saved.getId(), null, studentEmail, "STUDENT");
            } else if (result == PaymentStatus.FAILED) {
                self.markFailed(saved.getId(), null, studentEmail, "STUDENT");
            } else {
                // PENDING: Safaricom accepted the request, but accepted is not paid. The answer comes later
                // through the callback or the status checks, which find the payment by these saved ids.
                // Only the ids are written: an orphan callback may already have marked this payment PAID
                paymentRepository.saveCheckoutIds(saved.getId(), saved.getMerchantRequestId(), saved.getCheckoutRequestId());
            }

            // read again so the response shows the final status
            Payment finished = paymentRepository.findById(saved.getId())
                    .orElseThrow(() -> new PaymentNotFoundException(saved.getId()));
            return toResponse(finished);
        } catch (Exception e) {
            logger.error("Error while creating payment for booking with id {}: {}", request.getBookingId(), e.getMessage());
            throw e;
        }
    }

    // Marks a PENDING or UNKNOWN payment as PAID and moves the booking's next due date.
    // Transactional because the payment and the booking change together. The payment.paid event
    // (and so the landlord's email) waits until the transaction commits. Does nothing if the payment
    // is already PAID or FAILED, so a repeated answer can never count the same payment twice (idempotent).
    // The row is locked while it is read, so a callback and the status check job racing each other
    // cannot both see PENDING: the second one waits, then sees PAID and stops.
    // mpesaReceiptNumber is null when there is none (simulated provider, or the M-Pesa status check).
    // actorEmail and actorRole are the student when their own request finished the payment,
    // or SYSTEM when the answer came later from M-Pesa (callback or status check).
    @Transactional
    public void markPaid(Long paymentId, String mpesaReceiptNumber, String actorEmail, String actorRole) {
        try {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentId));

            if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.UNKNOWN) {
                logger.info("Payment {} is already {}, not marking it paid", paymentId, payment.getStatus());
                return;
            }

            applyPaid(payment, mpesaReceiptNumber, actorEmail, actorRole);
        } catch (Exception e) {
            logger.error("Error while marking payment with id {} as paid: {}", paymentId, e.getMessage());
            throw e;
        }
    }

    // Marks a PENDING or UNKNOWN payment as FAILED. Does nothing if it is already PAID or FAILED.
    // reason is a short text for the audit row (for example Safaricom's ResultDesc), or null.
    @Transactional
    public void markFailed(Long paymentId, String reason, String actorEmail, String actorRole) {
        try {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentId));

            if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.UNKNOWN) {
                logger.info("Payment {} is already {}, not marking it failed", paymentId, payment.getStatus());
                return;
            }

            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            String details = paymentDetails(payment);
            if (reason != null) {
                details = details + " failed: " + reason;
            }
            eventPublisher.publishEvent(new DomainEvent("payment.failed", actorEmail, actorRole,
                    "Payment", payment.getId(), shorten(details)));
        } catch (Exception e) {
            logger.error("Error while marking payment with id {} as failed: {}", paymentId, e.getMessage());
            throw e;
        }
    }

    // Marks a PENDING M-Pesa payment as UNKNOWN: Safaricom gave no definite answer after 3 status checks,
    // so the money may or may not have moved. Never FAILED here, silence is not a failure.
    // The status check job keeps asking about it for up to 1 hour. Does nothing unless the payment is PENDING.
    // payment.unknown only goes to the audit queue, nobody is emailed.
    @Transactional
    public void markUnknown(Long paymentId) {
        try {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentId));

            if (payment.getStatus() != PaymentStatus.PENDING) {
                logger.info("Payment {} is already {}, not marking it unknown", paymentId, payment.getStatus());
                return;
            }

            payment.setStatus(PaymentStatus.UNKNOWN);
            paymentRepository.save(payment);

            eventPublisher.publishEvent(new DomainEvent("payment.unknown", SYSTEM, SYSTEM, "Payment", payment.getId(),
                    paymentDetails(payment) + " unknown: no confirmation after 3 status checks"));
        } catch (Exception e) {
            logger.error("Error while marking payment with id {} as unknown: {}", paymentId, e.getMessage());
            throw e;
        }
    }

    // Orphan callback: Safaricom says this payment was paid, but its CheckoutRequestID was never saved
    // (the app stopped while calling Safaricom). The ids from the callback are stored on it and it is
    // marked PAID. The ONLY case where a FAILED payment may become PAID: it was failed because we had
    // nothing to ask Safaricom about, not because Safaricom said it failed.
    // Checked again under the row lock, so a repeated callback cannot apply it twice.
    @Transactional
    public void markOrphanPaid(Long paymentId, String merchantRequestId, String checkoutRequestId,
                               String mpesaReceiptNumber) {
        try {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentId));

            if (payment.getCheckoutRequestId() != null
                    || (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED)) {
                logger.info("Payment {} changed in the meantime, orphan callback not applied", paymentId);
                return;
            }

            payment.setMerchantRequestId(merchantRequestId);
            payment.setCheckoutRequestId(checkoutRequestId);
            logger.info("Orphan M-Pesa callback with CheckoutRequestID {} matched payment {}, marking it paid",
                    checkoutRequestId, paymentId);
            applyPaid(payment, mpesaReceiptNumber, SYSTEM, SYSTEM);
        } catch (Exception e) {
            logger.error("Error while applying an orphan callback to payment with id {}: {}", paymentId, e.getMessage());
            throw e;
        }
    }

    // the shared part of markPaid and markOrphanPaid. The caller has already checked the status
    private void applyPaid(Payment payment, String mpesaReceiptNumber, String actorEmail, String actorRole) {
        payment.setStatus(PaymentStatus.PAID);
        payment.setMpesaReceiptNumber(mpesaReceiptNumber);
        paymentRepository.save(payment);

        Booking booking = payment.getBooking();
        if (payment.getType() == PaymentType.DEPOSIT) {
            // the first month's rent is due on the day the stay starts
            booking.setNextDueDate(booking.getStartDate());
        } else {
            booking.setNextDueDate(booking.getNextDueDate().plusMonths(1));
        }
        bookingRepository.save(booking);

        eventPublisher.publishEvent(new DomainEvent("payment.paid", actorEmail, actorRole,
                "Payment", payment.getId(), paymentDetails(payment)));
    }

    // Handles the final answer Safaricom sends to POST /api/payments/callback.
    // Never throws: Safaricom must always get 200, whatever happens here.
    // Never log the callback itself, it contains the payer's phone number.
    // Only the CheckoutRequestID and the receipt number are logged.
    public void handleMpesaCallback(MpesaCallbackDto callback) {
        try {
            if (callback == null || callback.getBody() == null || callback.getBody().getStkCallback() == null) {
                logger.warn("M-Pesa callback ignored: it has no stkCallback");
                return;
            }

            MpesaCallbackDto.StkCallback stkCallback = callback.getBody().getStkCallback();
            String checkoutRequestId = stkCallback.getCheckoutRequestId();
            boolean success = Integer.valueOf(0).equals(stkCallback.getResultCode());

            Optional<Payment> found = checkoutRequestId == null
                    ? Optional.empty()
                    : paymentRepository.findByCheckoutRequestId(checkoutRequestId);
            if (found.isEmpty()) {
                if (success && checkoutRequestId != null) {
                    // the student paid, but we never saved this CheckoutRequestID
                    handleOrphanCallback(stkCallback);
                } else {
                    logger.warn("M-Pesa callback ignored: no payment with CheckoutRequestID {}", checkoutRequestId);
                }
                return;
            }

            // a repeated callback, or one arriving after the status checks already finished the payment.
            // PENDING and UNKNOWN payments are still open and may be finished by this callback
            Payment payment = found.get();
            if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.UNKNOWN) {
                if (payment.getStatus() == PaymentStatus.FAILED && success
                        && payment.getProvider() == PaymentProviderType.MPESA) {
                    // the student may have been charged although we gave up on the payment
                    logger.warn("M-Pesa callback for CheckoutRequestID {}: late success for a payment "
                            + "already marked FAILED, check by hand", checkoutRequestId);
                } else {
                    logger.info("M-Pesa callback ignored: payment with CheckoutRequestID {} is already {}",
                            checkoutRequestId, payment.getStatus());
                }
                return;
            }

            if (success) {
                self.markPaid(payment.getId(), findReceiptNumber(stkCallback), SYSTEM, SYSTEM);
            } else {
                self.markFailed(payment.getId(), stkCallback.getResultDesc(), SYSTEM, SYSTEM);
            }
        } catch (Exception e) {
            // not rethrown, the controller must still answer 200
            logger.error("Error while handling an M-Pesa callback: {}", e.getMessage());
        }
    }

    // A successful callback whose CheckoutRequestID matches no payment. Looks for exactly ONE payment
    // it can belong to (see PaymentRepository.findOrphanCandidates). With none or several, nothing is
    // changed: guessing could give the money to the wrong booking. The phone number is never logged.
    private void handleOrphanCallback(MpesaCallbackDto.StkCallback stkCallback) {
        String checkoutRequestId = stkCallback.getCheckoutRequestId();
        String receiptNumber = findReceiptNumber(stkCallback);

        Object phoneValue = findItemValue(stkCallback, "PhoneNumber");
        Object amountValue = findItemValue(stkCallback, "Amount");
        if (phoneValue == null || amountValue == null) {
            logger.warn("Orphan M-Pesa callback with CheckoutRequestID {} and receipt {} has no phone number "
                    + "or amount, not applied, check by hand", checkoutRequestId, receiptNumber);
            return;
        }

        List<Payment> candidates;
        try {
            String phoneNumber = PhoneUtil.normalize(phoneValue.toString());
            BigDecimal amount = new BigDecimal(amountValue.toString());
            candidates = paymentRepository.findOrphanCandidates(PaymentProviderType.MPESA,
                    List.of(PaymentStatus.PENDING, PaymentStatus.FAILED), phoneNumber,
                    amount.subtract(BigDecimal.ONE), amount, LocalDateTime.now().minusMinutes(ORPHAN_MATCH_MINUTES));
        } catch (InvalidPhoneNumberException | NumberFormatException e) {
            logger.warn("Orphan M-Pesa callback with CheckoutRequestID {} and receipt {} has an unreadable phone "
                    + "number or amount, not applied, check by hand", checkoutRequestId, receiptNumber);
            return;
        }

        if (candidates.size() != 1) {
            logger.warn("Orphan M-Pesa callback with CheckoutRequestID {} and receipt {} matches {} payments, "
                    + "not applied, check by hand", checkoutRequestId, receiptNumber, candidates.size());
            return;
        }

        self.markOrphanPaid(candidates.get(0).getId(), stkCallback.getMerchantRequestId(), checkoutRequestId,
                receiptNumber);
    }

    // the MpesaReceiptNumber item from a successful callback, or null if it is missing
    private String findReceiptNumber(MpesaCallbackDto.StkCallback stkCallback) {
        Object value = findItemValue(stkCallback, "MpesaReceiptNumber");
        return value == null ? null : value.toString();
    }

    // the value of one CallbackMetadata item, for example "Amount", or null if it is missing
    private Object findItemValue(MpesaCallbackDto.StkCallback stkCallback, String name) {
        if (stkCallback.getCallbackMetadata() == null || stkCallback.getCallbackMetadata().getItem() == null) {
            return null;
        }
        for (MpesaCallbackDto.Item item : stkCallback.getCallbackMetadata().getItem()) {
            if (name.equals(item.getName()) && item.getValue() != null) {
                return item.getValue();
            }
        }
        return null;
    }

    // audit details are short text, a long reason from a provider is cut to fit
    private String shorten(String text) {
        return text.length() > 250 ? text.substring(0, 250) : text;
    }

    public PagedResponse<PaymentResponseDto> getMyPayments(String studentEmail, int page, int size) {
        try {
            Page<Payment> payments = paymentRepository.findStudentPayments(studentEmail, buildPageable(page, size));
            return PagedResponse.from(payments.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while fetching payments for the logged in student: {}", e.getMessage());
            throw e;
        }
    }

    public PagedResponse<PaymentResponseDto> getReceivedPayments(String landlordEmail, int page, int size) {
        try {
            Page<Payment> payments = paymentRepository.findLandlordPayments(landlordEmail, buildPageable(page, size));
            return PagedResponse.from(payments.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while fetching payments for the logged in landlord: {}", e.getMessage());
            throw e;
        }
    }

    // every payment in the system, for the admin list. status is optional, the phone stays masked
    public PagedResponse<PaymentResponseDto> getAllPayments(PaymentStatus status, int page, int size) {
        try {
            Page<Payment> payments = paymentRepository.findAllPayments(status, buildPageable(page, size));
            return PagedResponse.from(payments.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while fetching all payments for an admin: {}", e.getMessage());
            throw e;
        }
    }

    // allowed for the student who paid, the landlord of the listing, and any admin
    public PaymentResponseDto getPayment(Long id, String userEmail, boolean isAdmin) {
        try {
            Payment payment = paymentRepository.findById(id)
                    .orElseThrow(() -> new PaymentNotFoundException(id));

            Booking booking = payment.getBooking();
            boolean isStudent = booking.getStudent().getEmail().equalsIgnoreCase(userEmail);
            boolean isLandlord = booking.getListing().getLandlord() != null
                    && booking.getListing().getLandlord().getEmail().equalsIgnoreCase(userEmail);

            if (!isAdmin && !isStudent && !isLandlord) {
                throw new PaymentOwnershipException("You are not allowed to view this payment");
            }
            return toResponse(payment);
        } catch (Exception e) {
            logger.error("Error while fetching payment with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    // only the type and provider go in events and audit rows, never the phone number
    private String paymentDetails(Payment payment) {
        return payment.getType() + " payment via " + payment.getProvider();
    }

    // page is at least 0, size is between 1 and 50, newest payments first
    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id").descending());
    }

    // the stored phone number is full, it is masked only here, on the way out
    private PaymentResponseDto toResponse(Payment payment) {
        PaymentResponseDto response = new PaymentResponseDto();
        response.setId(payment.getId());
        response.setBookingId(payment.getBooking().getId());
        response.setListingTitle(payment.getBooking().getListing().getTitle());
        response.setType(payment.getType());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus());
        response.setProvider(payment.getProvider());
        response.setPhoneNumber(MaskingUtil.maskPhoneNumber(payment.getPhoneNumber()));
        response.setMpesaReceiptNumber(payment.getMpesaReceiptNumber());
        response.setDateCreated(payment.getDateCreated());
        return response;
    }
}

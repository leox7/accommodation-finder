package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.model.Payment;
import com.Leon.accommodation_finder.model.PaymentProviderType;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.provider.MpesaPaymentProvider;
import com.Leon.accommodation_finder.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// The fallback for M-Pesa payments whose callback never arrives (for example ngrok was down).
// It asks Safaricom with STK Push Query, gently:
// - a PENDING payment is asked about at most 3 times, about 30, 60 and 120 seconds after it was created.
//   If the third check still has no answer, the payment becomes UNKNOWN, NOT FAILED: no answer is not a failure.
// - an UNKNOWN payment is asked about every 10 minutes, for up to 1 hour after it was created.
//   After that it becomes FAILED with "no confirmation after 1 hour".
// A paid or failed answer ends it at any point, and so does a callback that arrives in the meantime.
// After a restart, checks that were missed simply run on the next cycle, one query per payment per run.
// Only exists when payment.provider=mpesa.
@Service
@ConditionalOnProperty(name = "payment.provider", havingValue = "mpesa")
public class MpesaStatusCheckService {

    private static final Logger logger = LoggerFactory.getLogger(MpesaStatusCheckService.class);

    private static final int MAX_CHECKS = 3;
    // seconds after dateCreated when check number 1, 2 and 3 is due (index = statusChecks done so far)
    private static final int[] CHECK_DELAY_SECONDS = {30, 60, 120};
    // a payment that never got a CheckoutRequestID (the call to Safaricom never finished) is given up after this
    private static final int NO_CHECKOUT_ID_TIMEOUT_MINUTES = 2;
    private static final String TIMED_OUT = "timed out, no confirmation received";
    // how often an UNKNOWN payment is asked about, and for how long after it was created
    private static final int UNKNOWN_CHECK_EVERY_MINUTES = 10;
    private static final int UNKNOWN_GIVE_UP_MINUTES = 60;
    private static final String NO_CONFIRMATION_AFTER_1_HOUR = "no confirmation after 1 hour";

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final MpesaPaymentProvider mpesaPaymentProvider;

    @Autowired
    public MpesaStatusCheckService(PaymentRepository paymentRepository, PaymentService paymentService,
                                   MpesaPaymentProvider mpesaPaymentProvider) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.mpesaPaymentProvider = mpesaPaymentProvider;
    }

    // Runs 10 seconds after the previous run FINISHED, so two runs never overlap.
    @Scheduled(fixedDelay = 10000)
    public void checkPendingPayments() {
        List<Payment> pending = paymentRepository.findForStatusCheck(PaymentStatus.PENDING, PaymentProviderType.MPESA);
        for (Payment payment : pending) {
            // one broken payment must not stop the others from being checked
            try {
                checkPayment(payment);
            } catch (Exception e) {
                logger.error("Error while checking M-Pesa payment with id {}: {}", payment.getId(), e.getMessage());
            }
        }

        List<Payment> unknown = paymentRepository.findForStatusCheck(PaymentStatus.UNKNOWN, PaymentProviderType.MPESA);
        for (Payment payment : unknown) {
            try {
                checkUnknownPayment(payment);
            } catch (Exception e) {
                logger.error("Error while checking UNKNOWN M-Pesa payment with id {}: {}", payment.getId(), e.getMessage());
            }
        }
    }

    private void checkPayment(Payment payment) {
        LocalDateTime now = LocalDateTime.now();

        // the call to Safaricom never finished, so there is nothing to ask about
        if (payment.getCheckoutRequestId() == null) {
            if (now.isAfter(payment.getDateCreated().plusMinutes(NO_CHECKOUT_ID_TIMEOUT_MINUTES))) {
                logger.info("M-Pesa payment {} never got a CheckoutRequestID, marking it failed", payment.getId());
                paymentService.markFailed(payment.getId(), TIMED_OUT, PaymentService.SYSTEM, PaymentService.SYSTEM);
            }
            return;
        }

        int checksDone = payment.getStatusChecks();
        // all 3 checks ran but marking it UNKNOWN did not finish last time (for example the database was down)
        if (checksDone >= MAX_CHECKS) {
            paymentService.markUnknown(payment.getId());
            return;
        }

        LocalDateTime nextCheckDue = payment.getDateCreated().plusSeconds(CHECK_DELAY_SECONDS[checksDone]);
        if (now.isBefore(nextCheckDue)) {
            return;
        }

        // counted BEFORE asking, so even if something breaks halfway a payment is never asked about more than 3 times
        paymentRepository.addStatusCheck(payment.getId(), now);
        int checkNumber = checksDone + 1;

        PaymentStatus answer = mpesaPaymentProvider.queryStatus(payment);
        logger.info("M-Pesa status check {} of {} for payment {}: {}", checkNumber, MAX_CHECKS, payment.getId(),
                answer == PaymentStatus.PENDING ? "no answer yet" : answer);

        // the same methods as the callback, so a payment can never be counted twice
        if (answer == PaymentStatus.PAID) {
            paymentService.markPaid(payment.getId(), null, PaymentService.SYSTEM, PaymentService.SYSTEM);
        } else if (answer == PaymentStatus.FAILED) {
            paymentService.markFailed(payment.getId(), "confirmed by status check",
                    PaymentService.SYSTEM, PaymentService.SYSTEM);
        } else if (checkNumber == MAX_CHECKS) {
            // silence is not a failure: the student may have paid, so keep asking for a while longer
            paymentService.markUnknown(payment.getId());
        }
    }

    private void checkUnknownPayment(Payment payment) {
        LocalDateTime now = LocalDateTime.now();

        if (now.isAfter(payment.getDateCreated().plusMinutes(UNKNOWN_GIVE_UP_MINUTES))) {
            logger.info("M-Pesa payment {} is still unknown after 1 hour, marking it failed", payment.getId());
            paymentService.markFailed(payment.getId(), NO_CONFIRMATION_AFTER_1_HOUR,
                    PaymentService.SYSTEM, PaymentService.SYSTEM);
            return;
        }

        // asked less than 10 minutes ago, wait for the next turn
        if (payment.getLastCheckedAt() != null
                && now.isBefore(payment.getLastCheckedAt().plusMinutes(UNKNOWN_CHECK_EVERY_MINUTES))) {
            return;
        }

        paymentRepository.addStatusCheck(payment.getId(), now);

        PaymentStatus answer = mpesaPaymentProvider.queryStatus(payment);
        logger.info("M-Pesa status check for UNKNOWN payment {}: {}", payment.getId(),
                answer == PaymentStatus.PENDING ? "no answer yet" : answer);

        if (answer == PaymentStatus.PAID) {
            paymentService.markPaid(payment.getId(), null, PaymentService.SYSTEM, PaymentService.SYSTEM);
        } else if (answer == PaymentStatus.FAILED) {
            paymentService.markFailed(payment.getId(), "confirmed by status check",
                    PaymentService.SYSTEM, PaymentService.SYSTEM);
        }
    }
}

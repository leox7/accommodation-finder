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
// It asks Safaricom with STK Push Query, gently: at most 3 times per payment, about 30, 60 and
// 120 seconds after the payment was created. If the third check still has no answer, the payment
// is marked FAILED as timed out. Only exists when payment.provider=mpesa.
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
        List<Payment> payments = paymentRepository.findForStatusCheck(
                PaymentStatus.PENDING, PaymentProviderType.MPESA, MAX_CHECKS);

        for (Payment payment : payments) {
            // one broken payment must not stop the others from being checked
            try {
                checkPayment(payment);
            } catch (Exception e) {
                logger.error("Error while checking M-Pesa payment with id {}: {}", payment.getId(), e.getMessage());
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
        LocalDateTime nextCheckDue = payment.getDateCreated().plusSeconds(CHECK_DELAY_SECONDS[checksDone]);
        if (now.isBefore(nextCheckDue)) {
            return;
        }

        // counted BEFORE asking, so even if something breaks halfway a payment is never asked about more than 3 times
        paymentRepository.addStatusCheck(payment.getId());
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
            paymentService.markFailed(payment.getId(), TIMED_OUT, PaymentService.SYSTEM, PaymentService.SYSTEM);
        }
    }
}

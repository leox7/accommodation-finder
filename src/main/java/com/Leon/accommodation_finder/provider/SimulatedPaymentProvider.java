package com.Leon.accommodation_finder.provider;

import com.Leon.accommodation_finder.model.Payment;
import com.Leon.accommodation_finder.model.PaymentProviderType;
import com.Leon.accommodation_finder.model.PaymentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// A pretend provider for development: no real money moves.
// Spring only creates it when payment.provider=simulated, or when the setting is missing.
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentProvider implements PaymentProvider {

    // paying with this (normalized) number always fails, so the failure path can be tested
    private static final String FAILING_PHONE_NUMBER = "254700000000";

    @Override
    public PaymentProviderType getType() {
        return PaymentProviderType.SIMULATED;
    }

    // finishes straight away
    @Override
    public PaymentStatus startPayment(Payment payment) {
        if (FAILING_PHONE_NUMBER.equals(payment.getPhoneNumber())) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PAID;
    }
}

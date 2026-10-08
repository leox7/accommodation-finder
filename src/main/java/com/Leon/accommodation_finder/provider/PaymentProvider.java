package com.Leon.accommodation_finder.provider;

import com.Leon.accommodation_finder.model.Payment;
import com.Leon.accommodation_finder.model.PaymentProviderType;
import com.Leon.accommodation_finder.model.PaymentStatus;

// Something that can take money for a Payment. PaymentService only talks to this interface,
// so it does not care whether the money is simulated or comes from M-Pesa.
// Which implementation Spring uses is chosen by payment.provider in application.properties.
public interface PaymentProvider {

    // which provider this is, saved on the Payment
    PaymentProviderType getType();

    // Starts taking the money for a payment that is already saved as PENDING.
    // Returns PAID or FAILED when the answer is known at once, or PENDING when it comes later (M-Pesa).
    PaymentStatus startPayment(Payment payment);
}

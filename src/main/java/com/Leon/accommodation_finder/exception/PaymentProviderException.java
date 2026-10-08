package com.Leon.accommodation_finder.exception;

// The payment provider (M-Pesa) could not be reached or did not accept the request.
// The message is always this plain text, Safaricom's own error is never passed to the client.
public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException() {
        super("Payment provider is not reachable, please try again");
    }
}

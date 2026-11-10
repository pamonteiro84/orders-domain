package dev.paulo.payments_service.payment.exception;

import java.util.UUID;

public class PaymentInProgressException extends RuntimeException {

    public PaymentInProgressException(UUID orderId) {
        super("Payment for order " + orderId + " is in progress or already exists");
    }
}

package dev.paulo.payments_service.payment.exception;

import java.util.UUID;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(UUID orderId) {
        super("Idempotency key was already used for an order other than " + orderId);
    }
}

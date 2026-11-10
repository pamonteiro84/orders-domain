package dev.paulo.payments_service.payment.idempotency;

import dev.paulo.payments_service.payment.dto.PaymentResponse;
import java.util.UUID;

public record IdempotencyRecord(UUID orderId, Status status, PaymentResponse response) {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    public static IdempotencyRecord inProgress(UUID orderId) {
        return new IdempotencyRecord(orderId, Status.IN_PROGRESS, null);
    }

    public static IdempotencyRecord completed(UUID orderId, PaymentResponse response) {
        return new IdempotencyRecord(orderId, Status.COMPLETED, response);
    }
}

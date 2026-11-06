package dev.paulo.payments_service.payment.dto;

import dev.paulo.payments_service.payment.domain.Payment;
import dev.paulo.payments_service.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID orderId, PaymentStatus status, BigDecimal amount, Instant createdAt) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getCustomerId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCreatedAt());
    }
}

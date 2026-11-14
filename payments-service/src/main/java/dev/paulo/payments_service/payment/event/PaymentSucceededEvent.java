package dev.paulo.payments_service.payment.event;

import dev.paulo.payments_service.common.event.DomainEvent;
import dev.paulo.payments_service.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId, String type, Instant occurredAt, UUID paymentId, UUID orderId, UUID customerId, BigDecimal amount)
        implements DomainEvent {

    public static final String TYPE = "payment.succeeded.v1";

    public static PaymentSucceededEvent from(Payment payment) {
        return new PaymentSucceededEvent(
                UUID.randomUUID(),
                TYPE,
                payment.getCreatedAt(),
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount());
    }

    @Override
    public UUID aggregateId() {
        return orderId;
    }
}

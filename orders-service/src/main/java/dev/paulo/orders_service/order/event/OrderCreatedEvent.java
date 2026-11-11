package dev.paulo.orders_service.order.event;

import dev.paulo.orders_service.common.event.DomainEvent;
import dev.paulo.orders_service.order.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId, String type, Instant occurredAt, UUID orderId, UUID customerId, BigDecimal amount)
        implements DomainEvent {

    public static final String TYPE = "order.created.v1";

    public static OrderCreatedEvent from(Order order) {
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                TYPE,
                order.getCreatedAt(),
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount());
    }

    @Override
    public UUID aggregateId() {
        return orderId;
    }
}

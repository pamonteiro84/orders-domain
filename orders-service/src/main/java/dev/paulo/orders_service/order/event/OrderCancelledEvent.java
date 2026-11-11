package dev.paulo.orders_service.order.event;

import dev.paulo.orders_service.common.event.DomainEvent;
import dev.paulo.orders_service.order.domain.Order;
import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(UUID eventId, String type, Instant occurredAt, UUID orderId, UUID customerId)
        implements DomainEvent {

    public static final String TYPE = "order.cancelled.v1";

    public static OrderCancelledEvent from(Order order) {
        return new OrderCancelledEvent(
                UUID.randomUUID(), TYPE, order.getUpdatedAt(), order.getId(), order.getCustomerId());
    }

    @Override
    public UUID aggregateId() {
        return orderId;
    }
}

package dev.paulo.orders_service.order.exception;

import dev.paulo.orders_service.order.domain.OrderStatus;
import java.util.UUID;

public class InvalidOrderTransitionException extends RuntimeException {
    private final UUID orderId;
    private final OrderStatus currentStatus;

    public InvalidOrderTransitionException(UUID orderId, OrderStatus currentStatus) {
        super("Cannot cancel order " + orderId + " in status " + currentStatus);
        this.orderId = orderId;
        this.currentStatus = currentStatus;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }
}

package dev.paulo.orders_service.order.exception;

import dev.paulo.orders_service.order.domain.OrderStatus;
import java.util.UUID;

public class InvalidOrderTransitionException extends RuntimeException {
    private final UUID orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus targetStatus;

    public InvalidOrderTransitionException(UUID orderId, OrderStatus currentStatus, OrderStatus targetStatus) {
        super("Cannot move order " + orderId + " from " + currentStatus + " to " + targetStatus);
        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getTargetStatus() {
        return targetStatus;
    }
}

package dev.paulo.orders_service.order.exception;

import dev.paulo.orders_service.order.domain.OrderStatus;
import java.util.UUID;

public class InvalidOrderTransitionException extends RuntimeException {
    private final UUID orderId;
    private final OrderStatus status;

    public InvalidOrderTransitionException(UUID orderId, OrderStatus status) {
        super("Cannot cancel order: " + orderId + " in status " + status);
        this.orderId = orderId;
        this.status = status;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getOrderStatus() {
        return status;
    }
}

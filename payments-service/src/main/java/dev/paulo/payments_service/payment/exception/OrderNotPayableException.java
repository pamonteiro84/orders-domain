package dev.paulo.payments_service.payment.exception;

import dev.paulo.payments_service.order_view.domain.OrderViewStatus;
import java.util.UUID;

public class OrderNotPayableException extends RuntimeException {

    private final UUID orderId;
    private final OrderViewStatus status;

    public OrderNotPayableException(UUID orderId, OrderViewStatus status) {
        super("Order " + orderId + " cannot be paid in status " + status);
        this.orderId = orderId;
        this.status = status;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderViewStatus getStatus() {
        return status;
    }
}

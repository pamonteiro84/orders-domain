package dev.paulo.orders_service.order.dto;

import dev.paulo.orders_service.order.domain.Order;
import dev.paulo.orders_service.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(UUID id, OrderStatus status, BigDecimal totalAmount, Instant createdAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt());
    }
}

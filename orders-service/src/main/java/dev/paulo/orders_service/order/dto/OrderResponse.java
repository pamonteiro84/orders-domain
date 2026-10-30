package dev.paulo.orders_service.order.dto;

import dev.paulo.orders_service.order.domain.Order;
import dev.paulo.orders_service.order.domain.OrderItem;
import dev.paulo.orders_service.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id, UUID customerId, OrderStatus status, BigDecimal totalAmount, Instant createdAt, List<Item> items) {
    public record Item(UUID productId, int quantity, BigDecimal unitPrice) {

        static Item from(OrderItem item) {
            return new Item(item.getProductId(), item.getQuantity(), item.getUnitPrice());
        }
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getItems().stream().map(Item::from).toList());
    }
}

package dev.paulo.orders_service.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;
import java.util.List;

public record CreateOrderRequest (
    @NotNull UUID customerId,
    @NotEmpty List<@Valid Item> items
 ) {
    public record Item(
         @NotNull UUID productId,
         @Positive int quantity
    ) {}
}

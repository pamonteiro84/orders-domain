package dev.paulo.orders_service.order.controller;

import dev.paulo.orders_service.order.dto.CreateOrderRequest;
import dev.paulo.orders_service.order.dto.OrderResponse;
import dev.paulo.orders_service.order.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.create(request);
        URI location = URI.create("/orders/" + response.id());
        return ResponseEntity.created(location).body(response);
    }
}

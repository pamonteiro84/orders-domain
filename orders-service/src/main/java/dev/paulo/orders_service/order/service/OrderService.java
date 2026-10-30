package dev.paulo.orders_service.order.service;

import dev.paulo.orders_service.order.domain.Order;
import dev.paulo.orders_service.order.dto.CreateOrderRequest;
import dev.paulo.orders_service.order.dto.OrderResponse;
import dev.paulo.orders_service.order.exception.ProductNotFoundException;
import dev.paulo.orders_service.order.repository.OrderRepository;
import dev.paulo.orders_service.product.domain.Product;
import dev.paulo.orders_service.product.repository.ProductRepository;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;

    public OrderService(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {

        var productIds =
                request.items().stream().map(CreateOrderRequest.Item::productId).collect(Collectors.toSet());

        Map<UUID, Product> productsById = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Order order = new Order(request.customerId());

        for (var item : request.items()) {
            Product product = productsById.get(item.productId());
            if (product == null) {
                throw new ProductNotFoundException(item.productId());
            }
            order.addItem(product.getId(), item.quantity(), product.getPrice());
        }

        return OrderResponse.from(orders.save(order));
    }
}

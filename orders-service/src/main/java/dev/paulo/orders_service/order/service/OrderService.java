package dev.paulo.orders_service.order.service;

import dev.paulo.orders_service.common.PageResponse;
import dev.paulo.orders_service.order.domain.Order;
import dev.paulo.orders_service.order.dto.CreateOrderRequest;
import dev.paulo.orders_service.order.dto.OrderResponse;
import dev.paulo.orders_service.order.dto.OrderSummaryResponse;
import dev.paulo.orders_service.order.exception.OrderNotFoundException;
import dev.paulo.orders_service.order.exception.ProductNotFoundException;
import dev.paulo.orders_service.order.repository.OrderRepository;
import dev.paulo.orders_service.product.domain.Product;
import dev.paulo.orders_service.product.repository.ProductRepository;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;

    private static final int MAX_PAGE_SIZE = 100;

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

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return orders.findWithItemsById(id).map(OrderResponse::from).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(UUID customerId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(orders.findByCustomerId(customerId, pageable).map(OrderSummaryResponse::from));
    }

    @Transactional
    public OrderResponse cancel(UUID id) {
        Order order = orders.findWithItemsById(id).orElseThrow(() -> new OrderNotFoundException(id));
        order.cancel();
        return OrderResponse.from(order);
    }
}

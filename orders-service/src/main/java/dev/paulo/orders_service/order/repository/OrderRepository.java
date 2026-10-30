package dev.paulo.orders_service.order.repository;

import dev.paulo.orders_service.order.domain.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {}

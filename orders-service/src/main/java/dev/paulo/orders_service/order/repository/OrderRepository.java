package dev.paulo.orders_service.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.paulo.orders_service.order.domain.Order;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    
}

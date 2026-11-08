package dev.paulo.payments_service.order_view.repository;

import dev.paulo.payments_service.order_view.domain.OrderView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderViewRepository extends JpaRepository<OrderView, UUID> {
    Optional<OrderView> findByOrderIdAndCustomerId(UUID orderId, UUID customerId);
}

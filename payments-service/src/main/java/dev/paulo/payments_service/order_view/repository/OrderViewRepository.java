package dev.paulo.payments_service.order_view.repository;

import dev.paulo.payments_service.order_view.domain.OrderView;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OrderViewRepository extends JpaRepository<OrderView, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderView> findForUpdateByOrderIdAndCustomerId(UUID orderId, UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderView> findForUpdateByOrderId(UUID orderId);
}

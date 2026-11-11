package dev.paulo.orders_service.outbox.repository;

import dev.paulo.orders_service.outbox.domain.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {}

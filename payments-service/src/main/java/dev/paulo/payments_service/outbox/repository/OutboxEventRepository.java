package dev.paulo.payments_service.outbox.repository;

import dev.paulo.payments_service.outbox.domain.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findFirst100ByPublishedAtIsNullOrderByCreatedAtAsc();

    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}

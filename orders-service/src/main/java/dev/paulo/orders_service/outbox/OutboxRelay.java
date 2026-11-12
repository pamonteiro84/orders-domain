package dev.paulo.orders_service.outbox;

import dev.paulo.orders_service.outbox.domain.OutboxEvent;
import dev.paulo.orders_service.outbox.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {

    private static final String TOPIC = "orders";
    private static final Duration RETENTION = Duration.ofDays(7);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() throws Exception {
        for (OutboxEvent event : outbox.findFirst100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            kafka.send(TOPIC, event.getAggregateId().toString(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            event.markPublished();
        }
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void deleteOldPublished() {
        outbox.deletePublishedBefore(Instant.now().minus(RETENTION));
    }
}

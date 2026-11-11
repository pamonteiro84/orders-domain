package dev.paulo.orders_service.outbox;

import dev.paulo.orders_service.common.event.DomainEvent;
import dev.paulo.orders_service.common.event.EventPublisher;
import dev.paulo.orders_service.outbox.domain.OutboxEvent;
import dev.paulo.orders_service.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OutboxEventWriter implements EventPublisher {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    public OutboxEventWriter(OutboxEventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        String payload = jsonMapper.writeValueAsString(event);
        repository.save(new OutboxEvent(event.aggregateId(), event.type(), payload));
    }
}

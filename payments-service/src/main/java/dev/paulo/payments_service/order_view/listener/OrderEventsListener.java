package dev.paulo.payments_service.order_view.listener;

import dev.paulo.payments_service.event.repository.ProcessedEventRepository;
import dev.paulo.payments_service.order_view.domain.OrderView;
import dev.paulo.payments_service.order_view.domain.OrderViewStatus;
import dev.paulo.payments_service.order_view.repository.OrderViewRepository;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderEventsListener {

    private static final String TOPIC = "orders";
    private static final String CREATED = "order.created.v1";
    private static final String CANCELLED = "order.cancelled.v1";

    private final OrderViewRepository orderViews;
    private final ProcessedEventRepository processedEvents;
    private final JsonMapper jsonMapper;

    public OrderEventsListener(
            OrderViewRepository orderViews, ProcessedEventRepository processedEvents, JsonMapper jsonMapper) {
        this.orderViews = orderViews;
        this.processedEvents = processedEvents;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = TOPIC)
    @Transactional
    public void on(String message) {
        JsonNode event = jsonMapper.readTree(message);
        UUID eventId = UUID.fromString(event.get("eventId").asString());

        if (processedEvents.markProcessed(eventId) == 0) {
            return;
        }

        switch (event.get("type").asString()) {
            case CREATED -> applyCreated(event);
            case CANCELLED -> applyCancelled(event);
            default -> {}
        }
    }

    private void applyCreated(JsonNode event) {
        orderViews.save(new OrderView(
                UUID.fromString(event.get("orderId").asString()),
                UUID.fromString(event.get("customerId").asString()),
                event.get("amount").decimalValue(),
                OrderViewStatus.PENDING));
    }

    private void applyCancelled(JsonNode event) {
        orderViews.findById(UUID.fromString(event.get("orderId").asString())).ifPresent(OrderView::cancel);
    }
}

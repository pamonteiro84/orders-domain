package dev.paulo.orders_service.order.listener;

import dev.paulo.orders_service.inbox.repository.ProcessedEventRepository;
import dev.paulo.orders_service.order.domain.Order;
import dev.paulo.orders_service.order.domain.OrderStatus;
import dev.paulo.orders_service.order.repository.OrderRepository;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentEventsListener {

    private static final String TOPIC = "payments";
    private static final String SUCCEEDED = "payment.succeeded.v1";
    private static final String FAILED = "payment.failed.v1";

    private final OrderRepository orders;
    private final ProcessedEventRepository processedEvents;
    private final JsonMapper jsonMapper;

    public PaymentEventsListener(
            OrderRepository orders, ProcessedEventRepository processedEvents, JsonMapper jsonMapper) {
        this.orders = orders;
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
            case SUCCEEDED -> applySucceeded(event);
            case FAILED -> {}
            default -> {}
        }
    }

    private void applySucceeded(JsonNode event) {
        orders.findById(UUID.fromString(event.get("orderId").asString()))
                .filter(order -> order.getStatus() == OrderStatus.PENDING)
                .ifPresent(Order::markPaid);
    }
}

package dev.paulo.payments_service.order_view.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.paulo.payments_service.TestcontainersConfiguration;
import dev.paulo.payments_service.event.repository.ProcessedEventRepository;
import dev.paulo.payments_service.order_view.domain.OrderView;
import dev.paulo.payments_service.order_view.domain.OrderViewStatus;
import dev.paulo.payments_service.order_view.repository.OrderViewRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderEventsListenerTest {

    private static final String TOPIC = "orders";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private OrderViewRepository orderViews;

    @Autowired
    private ProcessedEventRepository processedEvents;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @Test
    void on_orderCreated_insertsPendingOrderViewWithAmount() throws Exception {
        UUID eventId = UUID.randomUUID();

        send(created(eventId));

        OrderView order = awaitOrderView();
        assertThat(order.getStatus()).isEqualTo(OrderViewStatus.PENDING);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getAmount()).isEqualByComparingTo("39.99");
        assertThat(processedEvents.existsById(eventId)).isTrue();
    }

    @Test
    void on_orderCancelled_cancelsPendingOrderView() throws Exception {
        send(created(UUID.randomUUID()));
        send(cancelled(UUID.randomUUID()));

        await().atMost(TIMEOUT).until(this::currentStatus, status -> status == OrderViewStatus.CANCELLED);
    }

    @Test
    void on_sameEventTwice_appliesItOnlyOnce() throws Exception {
        UUID createdId = UUID.randomUUID();
        UUID marker = UUID.randomUUID();
        send(created(createdId));
        send(cancelled(UUID.randomUUID()));
        await().atMost(TIMEOUT).until(this::currentStatus, status -> status == OrderViewStatus.CANCELLED);

        send(created(createdId));
        send(unknownType(marker));
        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(marker));

        assertThat(currentStatus()).isEqualTo(OrderViewStatus.CANCELLED);
        assertThat(processedEvents.existsById(createdId)).isTrue();
    }

    private void send(String event) throws Exception {
        kafka.send(TOPIC, orderId.toString(), event).get(10, TimeUnit.SECONDS);
    }

    private OrderView awaitOrderView() {
        return await().atMost(TIMEOUT)
                .until(() -> orderViews.findById(orderId), Optional::isPresent)
                .orElseThrow();
    }

    private OrderViewStatus currentStatus() {
        return orderViews.findById(orderId).map(OrderView::getStatus).orElse(null);
    }

    private String created(UUID eventId) {
        return """
                {"eventId": "%s", "type": "order.created.v1", "occurredAt": "2026-11-12T10:00:00Z",
                 "orderId": "%s", "customerId": "%s", "amount": 39.99}
                """.formatted(eventId, orderId, customerId);
    }

    private String cancelled(UUID eventId) {
        return """
                {"eventId": "%s", "type": "order.cancelled.v1", "occurredAt": "2026-11-12T10:05:00Z",
                 "orderId": "%s", "customerId": "%s"}
                """.formatted(eventId, orderId, customerId);
    }

    private String unknownType(UUID eventId) {
        return """
                {"eventId": "%s", "type": "order.unknown.v1", "occurredAt": "2026-11-12T10:10:00Z",
                 "orderId": "%s"}
                """.formatted(eventId, orderId);
    }
}

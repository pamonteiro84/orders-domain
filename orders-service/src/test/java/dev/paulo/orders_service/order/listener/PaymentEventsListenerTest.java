package dev.paulo.orders_service.order.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.paulo.orders_service.TestcontainersConfiguration;
import dev.paulo.orders_service.inbox.repository.ProcessedEventRepository;
import dev.paulo.orders_service.order.domain.Order;
import dev.paulo.orders_service.order.domain.OrderStatus;
import dev.paulo.orders_service.order.repository.OrderRepository;
import dev.paulo.orders_service.user.domain.User;
import dev.paulo.orders_service.user.repository.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentEventsListenerTest {

    private static final String TOPIC = "payments";
    private static final String DLT = "payments-dlt";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Test
    void on_paymentSucceeded_marksPendingOrderPaid() throws Exception {
        UUID orderId = pendingOrder();
        UUID eventId = UUID.randomUUID();

        send(orderId, succeeded(eventId, orderId));

        await().atMost(TIMEOUT).until(() -> statusOf(orderId), status -> status == OrderStatus.PAID);
        assertThat(processedEvents.existsById(eventId)).isTrue();
    }

    @Test
    void on_paymentFailed_keepsOrderPending() throws Exception {
        UUID orderId = pendingOrder();
        UUID eventId = UUID.randomUUID();

        send(orderId, failed(eventId, orderId));

        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(eventId));
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void on_sameEventIdTwice_appliesItOnlyOnce() throws Exception {
        UUID first = pendingOrder();
        UUID second = pendingOrder();
        UUID eventId = UUID.randomUUID();
        UUID marker = UUID.randomUUID();
        send(first, succeeded(eventId, first));
        await().atMost(TIMEOUT).until(() -> statusOf(first), status -> status == OrderStatus.PAID);

        send(second, succeeded(eventId, second));
        send(second, failed(marker, second));
        await().atMost(TIMEOUT).until(() -> processedEvents.existsById(marker));

        assertThat(statusOf(second)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void on_paymentSucceededForCancelledOrder_keepsItCancelledAndKeepsConsuming() throws Exception {
        UUID cancelled = cancelledOrder();
        UUID pending = pendingOrder();
        UUID eventId = UUID.randomUUID();

        send(cancelled, succeeded(eventId, cancelled));
        send(pending, succeeded(UUID.randomUUID(), pending));

        await().atMost(TIMEOUT).until(() -> statusOf(pending), status -> status == OrderStatus.PAID);
        assertThat(statusOf(cancelled)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(processedEvents.existsById(eventId)).isTrue();
    }

    @Test
    void on_brokenEvent_goesToDltAndKeepsConsuming() throws Exception {
        UUID orderId = pendingOrder();
        UUID brokenId = UUID.randomUUID();

        send(orderId, withoutOrderId(brokenId));
        send(orderId, succeeded(UUID.randomUUID(), orderId));

        await().atMost(TIMEOUT).until(() -> statusOf(orderId), status -> status == OrderStatus.PAID);
        assertThat(processedEvents.existsById(brokenId)).isFalse();
        awaitInDlt(brokenId);
    }

    private void awaitInDlt(UUID eventId) {
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("dlt-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(DLT));
            List<String> values = new ArrayList<>();
            await().atMost(TIMEOUT).pollInSameThread().until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> values.add(record.value()));
                return values.stream().anyMatch(value -> value.contains(eventId.toString()));
            });
        }
    }

    private void send(UUID orderId, String event) throws Exception {
        kafka.send(TOPIC, orderId.toString(), event).get(10, TimeUnit.SECONDS);
    }

    private OrderStatus statusOf(UUID orderId) {
        return orders.findById(orderId).map(Order::getStatus).orElse(null);
    }

    private UUID pendingOrder() {
        return orders.save(new Order(newCustomer())).getId();
    }

    private UUID cancelledOrder() {
        Order order = new Order(newCustomer());
        order.cancel();
        return orders.save(order).getId();
    }

    private UUID newCustomer() {
        return users.save(new User("cliente-" + UUID.randomUUID() + "@x.pt", "hash"))
                .getId();
    }

    private String succeeded(UUID eventId, UUID orderId) {
        return """
                {"eventId": "%s", "type": "payment.succeeded.v1", "occurredAt": "2026-11-17T10:00:00Z",
                 "paymentId": "%s", "orderId": "%s", "customerId": "%s", "amount": 39.99}
                """.formatted(eventId, UUID.randomUUID(), orderId, UUID.randomUUID());
    }

    private String withoutOrderId(UUID eventId) {
        return """
                {"eventId": "%s", "type": "payment.succeeded.v1", "occurredAt": "2026-11-17T10:00:00Z"}
                """.formatted(eventId);
    }

    private String failed(UUID eventId, UUID orderId) {
        return """
                {"eventId": "%s", "type": "payment.failed.v1", "occurredAt": "2026-11-17T10:00:00Z",
                 "paymentId": "%s", "orderId": "%s", "customerId": "%s", "amount": 39.99}
                """.formatted(eventId, UUID.randomUUID(), orderId, UUID.randomUUID());
    }
}

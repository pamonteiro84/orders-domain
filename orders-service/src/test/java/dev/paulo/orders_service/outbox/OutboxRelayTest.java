package dev.paulo.orders_service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.paulo.orders_service.TestcontainersConfiguration;
import dev.paulo.orders_service.order.event.OrderCreatedEvent;
import dev.paulo.orders_service.outbox.domain.OutboxEvent;
import dev.paulo.orders_service.outbox.repository.OutboxEventRepository;
import dev.paulo.orders_service.user.domain.User;
import dev.paulo.orders_service.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OutboxRelayTest {

    private static final String ADAGIO = "00000000-0000-0000-0000-000000000001";
    private static final String TOPIC = "orders";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void publishPending_sendsEventToOrdersTopicKeyedByOrderIdAndMarksItPublished() throws Exception {
        UUID orderId = createOrder();

        awaitPublished(orderId);
        OutboxEvent event = outboxEventsFor(orderId).get(0);

        ConsumerRecord<String, String> message = readFromOrdersTopic(orderId.toString());

        assertThat(message.key()).isEqualTo(orderId.toString());
        assertThat(message.value()).isEqualTo(event.getPayload());
        JsonNode payload = jsonMapper.readTree(message.value());
        assertThat(payload.get("type").asString()).isEqualTo(OrderCreatedEvent.TYPE);
        assertThat(payload.get("orderId").asString()).isEqualTo(orderId.toString());
    }

    @Test
    void deletePublishedBefore_removesOnlyEventsPublishedBeforeCutoff() throws Exception {
        UUID older = createOrder();
        awaitPublished(older);
        UUID newer = createOrder();
        Instant cutoff = awaitPublished(newer);

        transactionTemplate.executeWithoutResult(status -> outbox.deletePublishedBefore(cutoff));

        assertThat(outboxEventsFor(older)).isEmpty();
        assertThat(outboxEventsFor(newer)).hasSize(1);
    }

    @Test
    void deleteOldPublished_keepsEventsPublishedWithinRetention() throws Exception {
        UUID orderId = createOrder();
        awaitPublished(orderId);

        relay.deleteOldPublished();

        assertThat(outboxEventsFor(orderId)).hasSize(1);
    }

    private Instant awaitPublished(UUID orderId) {
        return await().atMost(TIMEOUT)
                .until(
                        () -> outboxEventsFor(orderId).stream()
                                .findFirst()
                                .flatMap(OutboxEvent::getPublishedAt)
                                .orElse(null),
                        publishedAt -> publishedAt != null);
    }

    private ConsumerRecord<String, String> readFromOrdersTopic(String key) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> found = new ArrayList<>();

        try (Consumer<String, String> consumer =
                consumerFactory.createConsumer("outbox-relay-test-" + UUID.randomUUID(), null, null, properties)) {
            consumer.subscribe(List.of(TOPIC));
            await().atMost(TIMEOUT).pollInSameThread().until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(message -> {
                    if (key.equals(message.key())) {
                        found.add(message);
                    }
                });
                return !found.isEmpty();
            });
        }
        return found.get(0);
    }

    private List<OutboxEvent> outboxEventsFor(UUID orderId) {
        return outbox.findAll().stream()
                .filter(event -> event.getAggregateId().equals(orderId))
                .toList();
    }

    private UUID createOrder() throws Exception {
        String customer = newCustomer();
        String body = """
                { "items": [ { "productId": "%s", "quantity": 1 } ] }
                """.formatted(ADAGIO);

        String location = mockMvc.perform(post("/orders")
                        .with(as(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return UUID.fromString(location.substring("/orders/".length()));
    }

    private String newCustomer() {
        User user = users.save(new User("cliente-" + UUID.randomUUID() + "@x.pt", "hash"));
        return user.getId().toString();
    }

    private RequestPostProcessor as(String customerId) {
        return jwt().jwt(token -> token.subject(customerId));
    }
}

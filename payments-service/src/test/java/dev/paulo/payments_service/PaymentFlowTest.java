package dev.paulo.payments_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentFlowTest {

    private static final String ORDERS_TOPIC = "orders";
    private static final String PAYMENTS_TOPIC = "payments";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private JsonMapper jsonMapper;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();

    @Test
    void orderCreated_thenPay_publishesPaymentSucceeded() throws Exception {
        kafka.send(ORDERS_TOPIC, orderId.toString(), created()).get(10, TimeUnit.SECONDS);

        await().atMost(TIMEOUT).until(() -> pay() == 201);

        JsonNode event = jsonMapper.readTree(readFromTopic(PAYMENTS_TOPIC, orderId.toString()));
        assertThat(event.get("type").asString()).isEqualTo("payment.succeeded.v1");
        assertThat(event.get("customerId").asString()).isEqualTo(customer.toString());
        assertThat(event.get("amount").decimalValue()).isEqualByComparingTo("39.99");
    }

    private int pay() throws Exception {
        return mockMvc.perform(post("/payments")
                        .with(jwt().jwt(token -> token.subject(customer.toString())))
                        .header("Idempotency-Key", "flow-" + orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orderId": "%s" }
                                """.formatted(orderId)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String readFromTopic(String topic, String key) {
        List<String> found = new ArrayList<>();
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("flow-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(topic));
            await().atMost(TIMEOUT).pollInSameThread().until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        found.add(record.value());
                    }
                });
                return !found.isEmpty();
            });
        }
        return found.get(0);
    }

    private String created() {
        return """
                {"eventId": "%s", "type": "order.created.v1", "occurredAt": "2026-11-20T10:00:00Z",
                 "orderId": "%s", "customerId": "%s", "amount": 39.99}
                """.formatted(UUID.randomUUID(), orderId, customer);
    }
}

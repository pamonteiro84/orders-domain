package dev.paulo.orders_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderFlowTest {

    private static final String ADAGIO = "00000000-0000-0000-0000-000000000001";
    private static final String ORDERS_TOPIC = "orders";
    private static final String PAYMENTS_TOPIC = "payments";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void createOrder_thenPaymentSucceeded_orderIsPaid() throws Exception {
        String customer = newCustomer();

        UUID orderId = createOrder(customer);

        JsonNode created = jsonMapper.readTree(readFromTopic(ORDERS_TOPIC, orderId.toString()));
        assertThat(created.get("type").asString()).isEqualTo("order.created.v1");
        assertThat(created.get("customerId").asString()).isEqualTo(customer);
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo("39.99");

        kafka.send(PAYMENTS_TOPIC, orderId.toString(), succeeded(orderId, customer))
                .get(10, TimeUnit.SECONDS);

        await().atMost(TIMEOUT)
                .untilAsserted(() -> mockMvc.perform(get("/orders/" + orderId).with(as(customer)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("PAID")));
    }

    private UUID createOrder(String customer) throws Exception {
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

    private String succeeded(UUID orderId, String customer) {
        return """
                {"eventId": "%s", "type": "payment.succeeded.v1", "occurredAt": "2026-11-20T10:00:00Z",
                 "paymentId": "%s", "orderId": "%s", "customerId": "%s", "amount": 39.99}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), orderId, customer);
    }

    private RequestPostProcessor as(String customerId) {
        return jwt().jwt(token -> token.subject(customerId));
    }

    private String newCustomer() {
        return users.save(new User("cliente-" + UUID.randomUUID() + "@x.pt", "hash"))
                .getId()
                .toString();
    }
}

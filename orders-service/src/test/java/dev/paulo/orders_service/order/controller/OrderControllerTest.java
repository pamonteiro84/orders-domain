package dev.paulo.orders_service.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.paulo.orders_service.TestcontainersConfiguration;
import dev.paulo.orders_service.order.event.OrderCancelledEvent;
import dev.paulo.orders_service.order.event.OrderCreatedEvent;
import dev.paulo.orders_service.outbox.domain.OutboxEvent;
import dev.paulo.orders_service.outbox.repository.OutboxEventRepository;
import dev.paulo.orders_service.user.domain.User;
import dev.paulo.orders_service.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderControllerTest {

    private static final String ADAGIO = "00000000-0000-0000-0000-000000000001";
    private static final String FRAPPE = "00000000-0000-0000-0000-000000000004";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void createOrder_returns201WithLocationAndServerCalculatedTotal() throws Exception {
        String body = """
                {
                  "items": [
                    { "productId": "%s", "quantity": 2 },
                    { "productId": "%s", "quantity": 1 }
                  ]
                }
                """.formatted(ADAGIO, FRAPPE);

        mockMvc.perform(post("/orders")
                        .with(as(newCustomer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/orders/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(119.88))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void createOrder_withCustomerIdInBody_usesCustomerFromToken() throws Exception {
        String ana = newCustomer();
        String rui = newCustomer();
        String body = """
                { "customerId": "%s", "items": [ { "productId": "%s", "quantity": 1 } ] }
                """.formatted(rui, ADAGIO);

        mockMvc.perform(post("/orders")
                        .with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/orders").with(as(ana)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/orders").with(as(rui)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void createOrder_withZeroQuantity_returns400() throws Exception {
        String body = """
                { "items": [ { "productId": "%s", "quantity": 0 } ] }
                """.formatted(ADAGIO);

        mockMvc.perform(post("/orders")
                        .with(as(newCustomer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @Test
    void createOrder_withEmptyItems_returns400() throws Exception {
        String body = """
                { "items": [] }
                """;

        mockMvc.perform(post("/orders")
                        .with(as(newCustomer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_writesOrderCreatedEventToOutbox() throws Exception {
        String customer = newCustomer();
        String id = createOrderFor(customer);

        List<OutboxEvent> events = outboxEventsFor(UUID.fromString(id));

        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.getType()).isEqualTo(OrderCreatedEvent.TYPE);
        assertThat(event.getPublishedAt()).isEmpty();

        JsonNode payload = jsonMapper.readTree(event.getPayload());
        assertThat(payload.get("type").asString()).isEqualTo(OrderCreatedEvent.TYPE);
        assertThat(payload.get("orderId").asString()).isEqualTo(id);
        assertThat(payload.get("customerId").asString()).isEqualTo(customer);
        assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("39.99");
        assertThat(payload.get("eventId").asString()).isNotBlank();
        assertThat(payload.get("occurredAt").isString()).isTrue();
    }

    @Test
    void createOrder_withUnknownProduct_returns422AndWritesNoOutboxEvent() throws Exception {
        String unknown = "00000000-0000-0000-0000-000000000999";
        String body = """
                { "items": [ { "productId": "%s", "quantity": 1 } ] }
                """.formatted(unknown);
        long outboxBefore = outbox.count();

        mockMvc.perform(post("/orders")
                        .with(as(newCustomer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.productId").value(unknown));

        assertThat(outbox.count()).isEqualTo(outboxBefore);
    }

    @Test
    void getOrder_returns200WithItemsAndTotal() throws Exception {
        String customer = newCustomer();
        String body = """
                {
                  "items": [
                    { "productId": "%s", "quantity": 2 },
                    { "productId": "%s", "quantity": 1 }
                  ]
                }
                """.formatted(ADAGIO, FRAPPE);

        String location = mockMvc.perform(post("/orders")
                        .with(as(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(location).with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(119.88))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void getOrder_withUnknownId_returns404() throws Exception {
        String unknown = "00000000-0000-0000-0000-000000000999";

        mockMvc.perform(get("/orders/" + unknown).with(as(newCustomer())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.orderId").value(unknown));
    }

    @Test
    void getOrder_ofAnotherCustomer_returns404() throws Exception {
        String id = createOrderFor(newCustomer());

        mockMvc.perform(get("/orders/" + id).with(as(newCustomer())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"));
    }

    @Test
    void listOrders_returnsCustomerOrdersNewestFirstWithPageMetadata() throws Exception {
        String customer = newCustomer();
        String first = createOrderFor(customer);
        createOrderFor(customer);
        String last = createOrderFor(customer);
        createOrderFor(newCustomer()); // outro cliente: não deve aparecer

        mockMvc.perform(get("/orders").with(as(customer)).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(last))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/orders").with(as(customer)).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    @Test
    void listOrders_forCustomerWithoutOrders_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/orders").with(as(newCustomer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listOrders_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void cancelOrder_pending_returns200WithCancelledStatus() throws Exception {
        String customer = newCustomer();
        String id = createOrderFor(customer);

        mockMvc.perform(post("/orders/" + id + "/cancel").with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/orders/" + id).with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_alreadyCancelled_returns409() throws Exception {
        String customer = newCustomer();
        String id = createOrderFor(customer);
        mockMvc.perform(post("/orders/" + id + "/cancel").with(as(customer))).andExpect(status().isOk());

        mockMvc.perform(post("/orders/" + id + "/cancel").with(as(customer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.orderId").value(id))
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"));
    }

    @Test
    void cancelOrder_writesOneOrderCancelledEventToOutboxEvenIfRepeated() throws Exception {
        String customer = newCustomer();
        String id = createOrderFor(customer);

        mockMvc.perform(post("/orders/" + id + "/cancel").with(as(customer))).andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + id + "/cancel").with(as(customer))).andExpect(status().isConflict());

        List<OutboxEvent> cancelled = outboxEventsFor(UUID.fromString(id)).stream()
                .filter(event -> event.getType().equals(OrderCancelledEvent.TYPE))
                .toList();

        assertThat(cancelled).hasSize(1);
        OutboxEvent event = cancelled.get(0);
        assertThat(event.getPublishedAt()).isEmpty();

        JsonNode payload = jsonMapper.readTree(event.getPayload());
        assertThat(payload.get("type").asString()).isEqualTo(OrderCancelledEvent.TYPE);
        assertThat(payload.get("orderId").asString()).isEqualTo(id);
        assertThat(payload.get("customerId").asString()).isEqualTo(customer);
        assertThat(payload.get("eventId").asString()).isNotBlank();
        assertThat(payload.get("occurredAt").isString()).isTrue();
    }

    @Test
    void cancelOrder_withUnknownId_returns404() throws Exception {
        String unknown = "00000000-0000-0000-0000-000000000999";

        mockMvc.perform(post("/orders/" + unknown + "/cancel").with(as(newCustomer())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.orderId").value(unknown));
    }

    @Test
    void cancelOrder_ofAnotherCustomer_returns404AndKeepsOrderPending() throws Exception {
        String owner = newCustomer();
        String id = createOrderFor(owner);

        mockMvc.perform(post("/orders/" + id + "/cancel").with(as(newCustomer())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/orders/" + id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private String newCustomer() {
        User user = users.save(new User("cliente-" + UUID.randomUUID() + "@x.pt", "hash"));
        return user.getId().toString();
    }

    private List<OutboxEvent> outboxEventsFor(UUID orderId) {
        return outbox.findAll().stream()
                .filter(event -> event.getAggregateId().equals(orderId))
                .toList();
    }

    private RequestPostProcessor as(String customerId) {
        return jwt().jwt(token -> token.subject(customerId));
    }

    private String createOrderFor(String customerId) throws Exception {
        String body = """
                { "items": [ { "productId": "%s", "quantity": 1 } ] }
                """.formatted(ADAGIO);

        String location = mockMvc.perform(post("/orders")
                        .with(as(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return location.substring("/orders/".length());
    }
}

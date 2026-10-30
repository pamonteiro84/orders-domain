package dev.paulo.orders_service.order.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.paulo.orders_service.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderControllerTest {

    private static final String ADAGIO = "00000000-0000-0000-0000-000000000001";
    private static final String FRAPPE = "00000000-0000-0000-0000-000000000004";
    private static final String CUSTOMER = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createOrder_returns201WithLocationAndServerCalculatedTotal() throws Exception {
        String body = """
                {
                  "customerId": "%s",
                  "items": [
                    { "productId": "%s", "quantity": 2 },
                    { "productId": "%s", "quantity": 1 }
                  ]
                }
                """.formatted(CUSTOMER, ADAGIO, FRAPPE);

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/orders/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(119.88))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void createOrder_withZeroQuantity_returns400() throws Exception {
        String body = """
                { "customerId": "%s", "items": [ { "productId": "%s", "quantity": 0 } ] }
                """.formatted(CUSTOMER, ADAGIO);

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_withEmptyItems_returns400() throws Exception {
        String body = """
                { "customerId": "%s", "items": [] }
                """.formatted(CUSTOMER);

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_withUnknownProduct_returns422() throws Exception {
        String unknown = "00000000-0000-0000-0000-000000000999";
        String body = """
                { "customerId": "%s", "items": [ { "productId": "%s", "quantity": 1 } ] }
                """.formatted(CUSTOMER, unknown);

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.productId").value(unknown));
    }

    @Test
    void getOrder_returns200WithItemsAndTotal() throws Exception {
        String body = """
                {
                  "customerId": "%s",
                  "items": [
                    { "productId": "%s", "quantity": 2 },
                    { "productId": "%s", "quantity": 1 }
                  ]
                }
                """.formatted(CUSTOMER, ADAGIO, FRAPPE);

        String location = mockMvc.perform(
                        post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(119.88))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void getOrder_withUnknownId_returns404() throws Exception {
        String unknown = "00000000-0000-0000-0000-000000000999";

        mockMvc.perform(get("/orders/" + unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.orderId").value(unknown));
    }

    @Test
    void listOrders_returnsCustomerOrdersNewestFirstWithPageMetadata() throws Exception {
        String customer = UUID.randomUUID().toString();
        String first = createOrderFor(customer);
        createOrderFor(customer);
        String last = createOrderFor(customer);
        createOrderFor(UUID.randomUUID().toString()); // outro cliente: não deve aparecer

        mockMvc.perform(get("/orders").param("customerId", customer).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(last))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/orders")
                        .param("customerId", customer)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    @Test
    void listOrders_forCustomerWithoutOrders_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/orders").param("customerId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listOrders_withoutCustomerId_returns400() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(status().isBadRequest());
    }

    private String createOrderFor(String customerId) throws Exception {
        String body = """
                { "customerId": "%s", "items": [ { "productId": "%s", "quantity": 1 } ] }
                """.formatted(customerId, ADAGIO);

        String location = mockMvc.perform(
                        post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        return location.substring("/orders/".length());
    }
}

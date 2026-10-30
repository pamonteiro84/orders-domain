package dev.paulo.orders_service.order.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.paulo.orders_service.TestcontainersConfiguration;
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
}

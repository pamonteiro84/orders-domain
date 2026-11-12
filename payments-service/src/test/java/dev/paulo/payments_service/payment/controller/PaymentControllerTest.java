package dev.paulo.payments_service.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.paulo.payments_service.TestcontainersConfiguration;
import dev.paulo.payments_service.order_view.domain.OrderView;
import dev.paulo.payments_service.order_view.domain.OrderViewStatus;
import dev.paulo.payments_service.order_view.repository.OrderViewRepository;
import dev.paulo.payments_service.payment.provider.PaymentProvider;
import dev.paulo.payments_service.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentControllerTest {

    private static final BigDecimal AMOUNT = new BigDecimal("39.99");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderViewRepository orderViews;

    @Autowired
    private PaymentRepository payments;

    @MockitoSpyBean
    private StringRedisTemplate redis;

    @MockitoSpyBean
    private PaymentProvider provider;

    private final UUID customer = UUID.randomUUID();

    @Test
    void pay_pendingOrder_returns201WithAmountFromOrderView() throws Exception {
        UUID order = pendingOrder(customer);

        pay(customer, "k1", order)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/payments/")))
                .andExpect(jsonPath("$.orderId").value(order.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amount").value(39.99));

        assertThat(paymentsOf(order)).isEqualTo(1);
    }

    @Test
    void pay_succeeded_marksOrderPaidAndRejectsAnotherKeyWithoutCharging() throws Exception {
        UUID order = pendingOrder(customer);

        pay(customer, "k1", order).andExpect(status().isCreated());

        assertThat(orderViews.findById(order).orElseThrow().getStatus()).isEqualTo(OrderViewStatus.PAID);

        pay(customer, "k2", order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Order cannot be paid"));

        assertThat(paymentsOf(order)).isEqualTo(1);
        verify(provider, times(1)).charge(eq(order), any(), any());
    }

    @Test
    void pay_withoutIdempotencyKey_returns400() throws Exception {
        UUID order = pendingOrder(customer);

        mockMvc.perform(request(customer, order)).andExpect(status().isBadRequest());

        assertThat(paymentsOf(order)).isZero();
    }

    @Test
    void pay_orderOfAnotherCustomer_returns404AndFreesKey() throws Exception {
        UUID order = pendingOrder(UUID.randomUUID());

        pay(customer, "k1", order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"));

        assertThat(paymentsOf(order)).isZero();
        assertThat(redis.hasKey(redisKey(customer, "k1"))).isFalse();
    }

    @Test
    void pay_reusedKey_returnsOriginalForSameOrderAnd422ForAnother() throws Exception {
        UUID order = pendingOrder(customer);
        String first = pay(customer, "k1", order)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String repeated = pay(customer, "k1", order)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID otherOrder = pendingOrder(customer);
        pay(customer, "k1", otherOrder)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Idempotency key already used"));

        UUID otherCustomer = UUID.randomUUID();
        pay(otherCustomer, "k1", pendingOrder(otherCustomer)).andExpect(status().isCreated());

        assertThat(repeated).isEqualTo(first);
        assertThat(paymentsOf(order)).isEqualTo(1);
        assertThat(paymentsOf(otherOrder)).isZero();
        verify(provider, times(1)).charge(eq(order), any(), eq("k1"));
    }

    @Test
    void pay_sameKeyWhileFirstIsCharging_returns409WithoutSecondCharge() throws Exception {
        UUID order = pendingOrder(customer);
        CountDownLatch charging = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        doAnswer(invocation -> {
                    charging.countDown();
                    finish.await(5, TimeUnit.SECONDS);
                    return invocation.callRealMethod();
                })
                .when(provider)
                .charge(eq(order), any(), any());

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Integer> first = executor.submit(() -> statusOf(customer, "k1", order));
            assertThat(charging.await(5, TimeUnit.SECONDS)).isTrue();

            pay(customer, "k1", order)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Payment in progress or already made"));

            finish.countDown();
            assertThat(first.get()).isEqualTo(201);
        }

        assertThat(paymentsOf(order)).isEqualTo(1);
        verify(provider, times(1)).charge(eq(order), any(), any());
    }

    @Test
    void pay_concurrentRequestsWithRedisDown_keepOnePayment() throws Exception {
        doThrow(new QueryTimeoutException("redis down")).when(redis).opsForValue();
        UUID order = pendingOrder(customer);
        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            requests.add(() -> statusOf(customer, "k1", order));
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (Future<Integer> result : executor.invokeAll(requests)) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(paymentsOf(order)).isEqualTo(1);
    }

    private ResultActions pay(UUID customerId, String key, UUID orderId) throws Exception {
        return mockMvc.perform(request(customerId, orderId).header("Idempotency-Key", key));
    }

    private int statusOf(UUID customerId, String key, UUID orderId) throws Exception {
        return pay(customerId, key, orderId).andReturn().getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder request(UUID customerId, UUID orderId) {
        return post("/payments")
                .with(as(customerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "orderId": "%s" }
                        """.formatted(orderId));
    }

    private RequestPostProcessor as(UUID customerId) {
        return jwt().jwt(token -> token.subject(customerId.toString()));
    }

    private UUID pendingOrder(UUID customerId) {
        UUID orderId = UUID.randomUUID();
        orderViews.save(new OrderView(orderId, customerId, AMOUNT, OrderViewStatus.PENDING));
        return orderId;
    }

    private long paymentsOf(UUID orderId) {
        return payments.findAll().stream()
                .filter(payment -> payment.getOrderId().equals(orderId))
                .count();
    }

    private String redisKey(UUID customerId, String key) {
        return "idem:" + customerId + ":" + key;
    }
}

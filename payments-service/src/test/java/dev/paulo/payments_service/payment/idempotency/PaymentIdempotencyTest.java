package dev.paulo.payments_service.payment.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import dev.paulo.payments_service.payment.domain.PaymentStatus;
import dev.paulo.payments_service.payment.dto.PaymentResponse;
import dev.paulo.payments_service.payment.exception.OrderNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyTest {

    private static final String KEY = "k1";

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> values;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final String redisKey = "idem:" + customerId + ":" + KEY;
    private final PaymentResponse paid = new PaymentResponse(
            UUID.randomUUID(), orderId, PaymentStatus.SUCCEEDED, new BigDecimal("39.99"), Instant.now());

    private PaymentIdempotency idempotency;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq(redisKey), anyString(), any(Duration.class))).thenReturn(true);
        idempotency = new PaymentIdempotency(redis, JsonMapper.builder().build());
    }

    @Test
    void execute_redisFailsOnComplete_stillReturnsResponse() {
        doThrow(new QueryTimeoutException("redis down"))
                .when(values)
                .set(eq(redisKey), anyString(), any(Duration.class));

        PaymentResponse response = idempotency.execute(customerId, KEY, orderId, () -> paid);

        assertThat(response).isEqualTo(paid);
    }

    @Test
    void execute_actionAndReleaseFail_rethrowsOriginalFailure() {
        when(redis.delete(redisKey)).thenThrow(new QueryTimeoutException("redis down"));
        OrderNotFoundException failure = new OrderNotFoundException(orderId);

        assertThatThrownBy(() -> idempotency.execute(customerId, KEY, orderId, () -> {
                    throw failure;
                }))
                .isSameAs(failure);
    }
}

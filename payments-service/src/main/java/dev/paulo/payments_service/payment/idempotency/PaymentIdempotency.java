package dev.paulo.payments_service.payment.idempotency;

import dev.paulo.payments_service.payment.dto.PaymentResponse;
import dev.paulo.payments_service.payment.exception.IdempotencyKeyReusedException;
import dev.paulo.payments_service.payment.exception.PaymentInProgressException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentIdempotency {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentIdempotency.class);
    private static final Duration IN_PROGRESS_TTL = Duration.ofSeconds(60);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    public PaymentIdempotency(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public PaymentResponse execute(UUID customerId, String key, UUID orderId, Supplier<PaymentResponse> action) {
        String redisKey = "idem:" + customerId + ":" + key;

        try {
            if (!reserve(redisKey, orderId)) {
                return replay(redisKey, orderId);
            }
        } catch (DataAccessException ex) {
            LOG.warn("Redis unavailable, processing payment without reservation", ex);
            return run(orderId, action);
        }

        PaymentResponse response;
        try {
            response = run(orderId, action);
        } catch (RuntimeException ex) {
            release(redisKey);
            throw ex;
        }

        complete(redisKey, orderId, response);
        return response;
    }

    private boolean reserve(String redisKey, UUID orderId) {
        String value = json.writeValueAsString(IdempotencyRecord.inProgress(orderId));
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey, value, IN_PROGRESS_TTL));
    }

    private PaymentResponse replay(String redisKey, UUID orderId) {
        String value = redis.opsForValue().get(redisKey);
        if (value == null) {
            throw new PaymentInProgressException(orderId);
        }

        IdempotencyRecord existing = json.readValue(value, IdempotencyRecord.class);
        if (!existing.orderId().equals(orderId)) {
            throw new IdempotencyKeyReusedException(orderId);
        }
        if (existing.status() == IdempotencyRecord.Status.IN_PROGRESS) {
            throw new PaymentInProgressException(orderId);
        }
        return existing.response();
    }

    private PaymentResponse run(UUID orderId, Supplier<PaymentResponse> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException ex) {
            throw new PaymentInProgressException(orderId);
        }
    }

    private void complete(String redisKey, UUID orderId, PaymentResponse response) {
        try {
            String value = json.writeValueAsString(IdempotencyRecord.completed(orderId, response));
            redis.opsForValue().set(redisKey, value, COMPLETED_TTL);
        } catch (DataAccessException ex) {
            LOG.warn("Could not store completed payment in Redis", ex);
        }
    }

    private void release(String redisKey) {
        try {
            redis.delete(redisKey);
        } catch (DataAccessException ex) {
            LOG.warn("Could not release idempotency key in Redis", ex);
        }
    }
}

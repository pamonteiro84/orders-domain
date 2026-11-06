package dev.paulo.payments_service.payment.provider;

import dev.paulo.payments_service.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {

    PaymentStatus charge(UUID orderId, BigDecimal amount);
}

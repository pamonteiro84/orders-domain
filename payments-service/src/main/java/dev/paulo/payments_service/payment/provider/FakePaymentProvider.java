package dev.paulo.payments_service.payment.provider;

import dev.paulo.payments_service.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FakePaymentProvider implements PaymentProvider {

    @Override
    public PaymentStatus charge(UUID orderId, BigDecimal amount) {
        return PaymentStatus.SUCCEEDED;
    }
}

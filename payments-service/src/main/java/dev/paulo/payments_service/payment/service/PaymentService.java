package dev.paulo.payments_service.payment.service;

import dev.paulo.payments_service.order_view.domain.OrderView;
import dev.paulo.payments_service.order_view.domain.OrderViewStatus;
import dev.paulo.payments_service.order_view.repository.OrderViewRepository;
import dev.paulo.payments_service.payment.domain.Payment;
import dev.paulo.payments_service.payment.domain.PaymentStatus;
import dev.paulo.payments_service.payment.dto.PaymentResponse;
import dev.paulo.payments_service.payment.exception.OrderNotFoundException;
import dev.paulo.payments_service.payment.exception.OrderNotPayableException;
import dev.paulo.payments_service.payment.provider.PaymentProvider;
import dev.paulo.payments_service.payment.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final OrderViewRepository orderViews;
    private final PaymentRepository payments;
    private final PaymentProvider provider;

    public PaymentService(OrderViewRepository orderViews, PaymentRepository payments, PaymentProvider provider) {
        this.orderViews = orderViews;
        this.payments = payments;
        this.provider = provider;
    }

    @Transactional
    public PaymentResponse pay(UUID customerId, UUID orderId, String idempotencyKey) {

        OrderView order = orderViews
                .findByOrderIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderViewStatus.PENDING) {
            throw new OrderNotPayableException(orderId, order.getStatus());
        }

        PaymentStatus status = provider.charge(orderId, order.getAmount(), idempotencyKey);
        Payment payment = new Payment(orderId, customerId, order.getAmount(), status);

        return PaymentResponse.from(payments.save(payment));
    }
}
